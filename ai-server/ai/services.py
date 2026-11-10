"""API 4개의 실제 처리 순서 (시간 예산 → 모델 호출 → 검사 → 필요하면 1회 재요청 → 보기 섞기)."""
import logging
import time
from zoneinfo import ZoneInfo

from django.conf import settings

from . import llm, mock, prompts
from .errors import MOCKABLE, AIError, InvalidOutput, ai_timeout, content_too_short, generation_failed, note_unreadable
from .images import fetch_images
from .schemas import LLMFeedback, LLMQuestionSet, LLMReviewSet, LLMSummary
from .validation import check_initial, check_review, clip, shuffle_options

log = logging.getLogger(__name__)
KST = ZoneInfo("Asia/Seoul")
EMPTY_ESSAY_FEEDBACK = "답을 적지 않았어요. 모범답안을 보고 핵심을 정리해 보세요."
MIN_TRUSTED_CHARS = 100  # 공부 내용이 이 이상이면 enoughContent=false를 믿지 않고 1회 재요청


class Budget:
    """메인 서버가 50초 기다리므로 45초 안에 끝낸다."""

    def __init__(self):
        self.started = time.monotonic()

    def elapsed(self) -> float:
        return time.monotonic() - self.started

    def remaining(self) -> float:
        return settings.AI_TOTAL_BUDGET_SECONDS - self.elapsed()

    def can_retry(self) -> bool:
        return self.elapsed() < settings.AI_RETRY_IF_ELAPSED_UNDER and self.remaining() > 10


def _maybe_mock_error(*texts) -> None:
    """Mock 모드에서 content나 memo에 'MOCK_ERROR:코드'를 넣으면 그 에러를 낸다 (메인 서버 에러 처리 테스트용)."""
    if not settings.AI_MOCK:
        return
    for text in texts:
        if text and "MOCK_ERROR:" in text:
            code = text.split("MOCK_ERROR:", 1)[1].split()[0].strip()
            if code in MOCKABLE:
                raise MOCKABLE[code]()


def _generate(budget: Budget, system: str, user: str, schema, *, images=None, mock_result=None):
    if settings.AI_MOCK:
        return mock_result()
    timeout = budget.remaining() - 1
    if timeout < 3:
        raise ai_timeout()
    result = llm.generate_json(system, user, schema, images=images, timeout_s=timeout)
    if budget.remaining() <= 0:
        raise ai_timeout()
    return result


def _with_retry(budget: Budget, build_user, call, check, label: str):
    """call(user) → check(result) 를 최대 2번. check는 (결과, 문제점 목록)을 돌려준다."""
    note = ""
    for attempt in (1, 2):
        try:
            result = call(build_user() + note)
            value, problems = check(result)
        except InvalidOutput:
            value, problems = None, ["JSON 형식이 올바르지 않음"]
        if not problems:
            if attempt == 2:
                log.info("%s: 재요청 후 통과", label)
            return value
        log.warning("%s 검증 실패(%d차): %s", label, attempt, "; ".join(problems)[:500])
        if attempt == 2 or not budget.can_retry():
            raise generation_failed()
        note = "\n\n[직전 출력의 문제 — 아래를 고쳐서 처음부터 다시 만들 것]\n" + "\n".join(f"- {p}" for p in problems)
    raise generation_failed()  # 도달하지 않음


# ---------- AI1 문제 출제 ----------
def make_questions(req) -> dict:
    budget = Budget()
    _maybe_mock_error(req.content, req.memo)

    images = []
    if req.inputType == "IMAGE" and not settings.AI_MOCK:
        images = fetch_images(req.imageUrls)

    system = prompts.questions_system(req)
    holder = {}

    def call(user):
        return _generate(budget, system, user, LLMQuestionSet, images=images,
                         mock_result=lambda: mock.question_set(req))

    def check(result: LLMQuestionSet):
        if not result.readable:
            raise note_unreadable() if req.inputType == "IMAGE" else content_too_short()
        holder["extracted"] = result.extractedText
        if not result.enoughContent:
            # 모델이 충분한 내용도 가끔 false로 줌 -> 내용이 길면 믿지 않고 다시 요청
            source = req.content if req.inputType == "TEXT" else result.extractedText
            length = len((source or "").strip())
            if length < MIN_TRUSTED_CHARS:
                raise content_too_short()
            return result.questions, [
                f"enoughContent를 false로 했지만 공부 내용이 {length}자로 충분함. "
                "enoughContent=true로 하고, 같은 개념을 정의·적용·계산·비교 등 다른 각도로 물어 7문제를 만들 것"
            ]
        questions, problems = check_initial(result.questions, req.excludeQuestions)
        # 사진이면 extractedText가 꼭 있어야 함 (메인 서버가 재도전·복습 출제에 다시 씀)
        if req.inputType == "IMAGE" and not (result.extractedText or "").strip():
            problems.append("사진 입력인데 extractedText가 비어 있음. 사진 속 공부 내용을 옮겨 적을 것")
        return questions, problems

    questions = _with_retry(budget, lambda: prompts.questions_user(req, req.excludeQuestions), call, check, "AI1")
    shuffle_options(questions)
    extracted = (holder.get("extracted") or "").strip() or None
    return {
        "readable": True,
        "extractedText": extracted if req.inputType == "IMAGE" else None,
        "questions": [q.model_dump() for q in questions],
    }


# ---------- AI2 피드백 ----------
def make_feedback(req) -> dict:
    budget = Budget()
    _maybe_mock_error(req.essay.essayText)
    system = prompts.feedback_system(req)

    def call(user):
        return _generate(budget, system, user, LLMFeedback, mock_result=lambda: mock.feedback(req))

    def check(result: LLMFeedback):
        summary = clip(result.summary, 200)
        essay = clip(result.essayFeedback, 300)
        problems = [] if summary else ["summary가 비어 있음"]
        if (req.essay.essayText or "").strip() and not essay:
            problems.append("essayFeedback이 비어 있음")
        return (summary, essay), problems

    summary, essay = _with_retry(budget, lambda: prompts.feedback_user(req), call, check, "AI2")
    if not (req.essay.essayText or "").strip():
        essay = EMPTY_ESSAY_FEEDBACK
    return {"summary": summary, "essayFeedback": essay}


# ---------- AI3 복습 출제 ----------
def make_review_questions(req) -> dict:
    budget = Budget()
    _maybe_mock_error(req.content, req.memo)
    n = len(req.weakTopics)
    if n >= 3:
        hint = "약한 주제 중 틀렸던 문제와 가장 관련 깊은 3개를 골라 주제마다 1문제씩 낸다."
    else:
        hint = (f"약한 주제가 {n}개뿐이므로 모든 약한 주제를 한 번 이상 쓰고, "
                "같은 주제를 다른 각도로 묻는 변형 문제로 3문제를 채운다.")
    system = prompts.review_system(req, hint)

    def call(user):
        return _generate(budget, system, user, LLMReviewSet, mock_result=lambda: mock.review_set(req))

    def check(result: LLMReviewSet):
        return check_review(result.questions, req.weakTopics, req.excludeQuestions)

    questions = _with_retry(budget, lambda: prompts.review_user(req), call, check, "AI3")
    shuffle_options(questions)
    return {"questions": [q.model_dump() for q in questions]}


# ---------- AI4 복습 피드백 ----------
def _korean_date(dt) -> str:
    d = dt.astimezone(KST) if dt.tzinfo else dt
    return f"{d.month}월 {d.day}일"


def make_review_feedback(req) -> dict:
    budget = Budget()
    studied, reviewed = _korean_date(req.studiedAt), _korean_date(req.reviewedAt)
    system = prompts.review_feedback_system()

    def call(user):
        return _generate(budget, system, user, LLMSummary,
                         mock_result=lambda: mock.review_summary(req, studied, reviewed))

    def check(result: LLMSummary):
        summary = clip(result.summary, 200)
        return summary, ([] if summary else ["summary가 비어 있음"])

    summary = _with_retry(budget, lambda: prompts.review_feedback_user(req, studied, reviewed), call, check, "AI4")
    return {"summary": summary}


__all__ = ["make_questions", "make_feedback", "make_review_questions", "make_review_feedback", "AIError"]

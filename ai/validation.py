"""모델 출력 검사·자동 수정·보기 섞기 (명세 4장).

고칠 수 있는 것(주제 길이, 번호, 형식 표시)은 바로 고치고,
고칠 수 없는 것만 problems로 돌려줘서 재요청 여부를 정하게 한다.
"""
import random
from collections import Counter

from .schemas import LLMQuestion

MAX_TOPIC = 100
INITIAL_ORDER = ["EASY", "EASY", "NORMAL", "NORMAL", "HARD", "HARD", "ESSAY"]
MAX_SAME_ANSWER = 3


def _norm(text: str) -> str:
    return "".join((text or "").split()).lower()


def _check_common(q: LLMQuestion, problems: list[str]) -> None:
    label = f"{q.number}번"
    q.topic = (q.topic or "").strip()[:MAX_TOPIC]
    q.content = (q.content or "").strip()
    if not q.topic:
        problems.append(f"{label}: topic이 비어 있음")
    if not q.content:
        problems.append(f"{label}: 문제 본문이 비어 있음")

    if q.difficulty == "ESSAY":
        q.questionType = "ESSAY"
        q.options, q.answerIndex, q.explanation = None, None, None
        if not (q.modelAnswer or "").strip():
            problems.append(f"{label}: 서술형 modelAnswer가 없음")
        points = [p.strip() for p in (q.gradingPoints or []) if p and p.strip()]
        if len(points) < 2:
            problems.append(f"{label}: 서술형 gradingPoints가 2개보다 적음")
        q.gradingPoints = points[:3]
    else:
        q.questionType = "MULTIPLE_CHOICE"
        q.modelAnswer, q.gradingPoints = None, None
        options = [(o or "").strip() for o in (q.options or [])]
        q.options = options
        if len(options) != 4:
            problems.append(f"{label}: 보기가 4개가 아님({len(options)}개)")
        elif len({_norm(o) for o in options}) != 4 or any(not o for o in options):
            problems.append(f"{label}: 보기가 비어 있거나 서로 겹침")
        if q.answerIndex is None or not 0 <= q.answerIndex <= 3:
            problems.append(f"{label}: answerIndex가 0~3이 아님")
        if not (q.explanation or "").strip():
            problems.append(f"{label}: 해설(explanation)이 없음")


def _check_excluded(questions: list[LLMQuestion], exclude: list[str], problems: list[str]) -> None:
    excluded = {_norm(e) for e in exclude if e}
    for q in questions:
        if _norm(q.content) in excluded:
            problems.append(f"{q.number}번: 이전에 낸 문제와 같음")


def check_initial(questions: list[LLMQuestion], exclude: list[str] | None = None):
    """첫 풀이·재도전 7문제 검사. (고친 문제 목록, 문제점 목록)을 돌려준다."""
    problems: list[str] = []
    if len(questions) != 7:
        return questions, [f"문제가 7개가 아님({len(questions)}개)"]

    counts = Counter(q.difficulty for q in questions)
    if counts != Counter(INITIAL_ORDER):
        return questions, [f"난이도 분포가 EASY2·NORMAL2·HARD2·ESSAY1이 아님({dict(counts)})"]

    # 번호는 난이도 순서대로 다시 매긴다 (쉬움 1·2, 보통 3·4, 어려움 5·6, 서술형 7)
    rank = {"EASY": 0, "NORMAL": 1, "HARD": 2, "ESSAY": 3}
    questions = sorted(questions, key=lambda q: (rank[q.difficulty], q.number))
    for i, q in enumerate(questions, start=1):
        q.number = i
        _check_common(q, problems)
    _check_excluded(questions, exclude or [], problems)
    return questions, problems


def check_review(questions: list[LLMQuestion], weak_topics: list[str], exclude: list[str] | None = None):
    """복습 3문제 검사. topic은 weakTopics 중 하나와 글자까지 같게 맞춘다."""
    problems: list[str] = []
    if len(questions) != 3:
        return questions, [f"복습 문제가 3개가 아님({len(questions)}개)"]

    canonical = {_norm(t): t for t in weak_topics}
    questions = sorted(questions, key=lambda q: q.number)
    for i, q in enumerate(questions, start=1):
        q.number = i
        if q.difficulty not in ("EASY", "NORMAL", "HARD"):
            q.difficulty = "NORMAL"
        _check_common(q, problems)
        matched = canonical.get(_norm(q.topic))
        if matched is None:
            problems.append(f"{i}번: topic '{q.topic}'이 weakTopics에 없음")
        else:
            q.topic = matched
    _check_excluded(questions, exclude or [], problems)
    return questions, problems


def shuffle_options(questions: list[LLMQuestion], rng: random.Random | None = None) -> None:
    """객관식 보기를 섞고 answerIndex를 새 위치로 바꾼다. 같은 정답 번호는 3번까지만."""
    rng = rng or random.Random()
    mc = [q for q in questions if q.questionType == "MULTIPLE_CHOICE"]
    if not mc:
        return
    while True:
        targets = [rng.randrange(4) for _ in mc]
        if max(Counter(targets).values()) <= MAX_SAME_ANSWER:
            break
    for q, target in zip(mc, targets):
        correct = q.options[q.answerIndex]
        others = [o for i, o in enumerate(q.options) if i != q.answerIndex]
        rng.shuffle(others)
        q.options = others[:target] + [correct] + others[target:]
        q.answerIndex = target


def clip(text: str, limit: int) -> str:
    text = (text or "").strip()
    if len(text) <= limit:
        return text
    cut = text[:limit]
    # 문장 중간에서 끊기지 않게 마지막 마침표까지 자른다
    for mark in (". ", "요.", "다.", "!", "?"):
        idx = cut.rfind(mark)
        if idx >= limit // 2:
            return cut[: idx + len(mark)].strip()
    return cut.rstrip() + "…"

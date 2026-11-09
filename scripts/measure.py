"""실제 AI 모델로 응답 시간·토큰·비용을 재는 스크립트.

사용법 (ai-server 폴더에서):
    export OPENAI_API_KEY=발급받은키          # 윈도우: $env:OPENAI_API_KEY="..."
    python scripts/measure.py --images 노트1.jpg 노트2.jpg 노트3.jpg 노트4.jpg 노트5.jpg --runs 3

- 사진 5장으로 문제 출제, 텍스트 5,000자로 문제 출제, 피드백·복습 출제를 차례로 재요.
- 기본은 GPT-6 Luna ($0.10 / $0.50 per 1M). Gemini로 재려면 AI_PROVIDER=gemini, GEMINI_API_KEY.
- 다른 가격은 --price-in, --price-out (100만 토큰당 달러)으로.
"""
import argparse
import os
import statistics
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
os.environ.setdefault("AI_MOCK", "false")

import django  # noqa: E402

django.setup()

from django.conf import settings  # noqa: E402

from ai import llm, services  # noqa: E402
from ai.errors import AIError  # noqa: E402
from ai.images import to_jpeg  # noqa: E402
from ai.schemas import FeedbackRequest, QuestionsRequest, ReviewQuestionsRequest  # noqa: E402

PARAGRAPH = (
    "이차함수 y = ax² + bx + c 의 그래프는 포물선이다. a > 0 이면 아래로 볼록하고 a < 0 이면 위로 볼록하다. "
    "완전제곱식 y = a(x − p)² + q 로 바꾸면 꼭짓점은 (p, q)이고 대칭축은 x = p 이다. "
    "판별식 D = b² − 4ac 가 양수이면 x축과 두 점에서 만나고, 0이면 한 점에서 접하며, 음수이면 만나지 않는다. "
    "y = ax² 의 그래프를 x축 방향으로 p, y축 방향으로 q만큼 평행이동하면 y = a(x − p)² + q 가 된다. "
    "x의 범위가 정해진 경우 최댓값과 최솟값은 꼭짓점과 범위의 양 끝값을 비교해서 구한다. "
)


PAUSE = 1


def sample_text(chars: int) -> str:
    text = ""
    while len(text) < chars:
        text += PARAGRAPH
    return text[:chars]


def run(label, func, runs, price_in, price_out, krw):
    rows = []
    for i in range(1, runs + 1):
        llm.USAGE_LOG.clear()
        started = time.monotonic()
        try:
            func()
            status = "성공"
        except AIError as err:
            status = f"{err.code}"
        elapsed = time.monotonic() - started
        tin = sum(u["input"] for u in llm.USAGE_LOG)
        tout = sum(u["output"] + u["thinking"] for u in llm.USAGE_LOG)
        cost = (tin * price_in + tout * price_out) / 1_000_000
        rows.append((elapsed, len(llm.USAGE_LOG), tin, tout, cost, status))
        print(f"  {label} {i}회: {elapsed:5.1f}초  호출 {len(llm.USAGE_LOG)}번  "
              f"입력 {tin:,} / 출력 {tout:,} 토큰  약 {cost * krw:,.1f}원  {status}")
        time.sleep(PAUSE)  # 요청 한도에 걸리지 않게
    times = [r[0] for r in rows]
    costs = [r[4] for r in rows]
    return {
        "label": label, "avg": statistics.mean(times), "max": max(times),
        "cost": statistics.mean(costs), "fails": sum(1 for r in rows if r[5] != "성공"),
    }


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--images", nargs="*", default=[], help="노트 사진 파일 (최대 5장)")
    p.add_argument("--runs", type=int, default=3)
    p.add_argument("--text-chars", type=int, default=5000)
    gemini = settings.AI_PROVIDER == "gemini"
    p.add_argument("--price-in", type=float, default=0.30 if gemini else 0.10, help="입력 100만 토큰당 달러")
    p.add_argument("--price-out", type=float, default=2.50 if gemini else 0.50, help="출력 100만 토큰당 달러")
    p.add_argument("--pause", type=float, default=13 if gemini else 1, help="호출 사이 쉬는 초 (무료 한도 대비)")
    p.add_argument("--krw", type=float, default=1400, help="1달러 원화")
    args = p.parse_args()

    key_name = "GEMINI_API_KEY" if gemini else "OPENAI_API_KEY"
    if not getattr(settings, key_name):
        sys.exit(f"{key_name}를 먼저 설정해 주세요.")
    global PAUSE
    PAUSE = args.pause
    effort = settings.AI_THINKING_BUDGET if gemini else settings.AI_REASONING_EFFORT
    print(f"모델: {settings.AI_PROVIDER} / {settings.AI_MODEL} / 추론: {effort or '모델 기본'}\n")

    results = []
    common = dict(quizSetType="INITIAL", subjectName="수학", studyLevel="HIGH",
                  memo="이차함수 꼭짓점, 판별식으로 x절편 개수 구하기")

    if args.images:
        paths = args.images[:5]
        t0 = time.monotonic()
        jpegs = [to_jpeg(Path(x).read_bytes()) for x in paths]
        print(f"사진 {len(paths)}장 변환: {time.monotonic() - t0:.1f}초 "
              f"(실제 서버는 S3 내려받기 1~2초가 더해짐)")
        services.fetch_images = lambda urls: jpegs  # 로컬 파일을 쓰도록 바꿔 끼움
        req = QuestionsRequest(**common, inputType="IMAGE", imageUrls=["https://local/x.jpg"] * len(paths))
        results.append(run(f"사진 {len(paths)}장 출제", lambda: services.make_questions(req),
                           args.runs, args.price_in, args.price_out, args.krw))

    req_text = QuestionsRequest(**common, inputType="TEXT", content=sample_text(args.text_chars))
    results.append(run(f"텍스트 {args.text_chars:,}자 출제", lambda: services.make_questions(req_text),
                       args.runs, args.price_in, args.price_out, args.krw))

    fb = FeedbackRequest.model_validate({
        "subjectName": "수학", "studyLevel": "HIGH", "correctCount": 3, "gradedCount": 6, "grade": "AUTUMN",
        "results": [{"number": i, "topic": t, "difficulty": d, "isCorrect": c} for i, (t, d, c) in enumerate([
            ("이차함수의 정의", "EASY", True), ("볼록 방향", "EASY", True), ("꼭짓점 좌표", "NORMAL", True),
            ("판별식과 x절편 개수", "NORMAL", False), ("평행이동", "HARD", False), ("범위 최솟값", "HARD", False)], 1)],
        "essay": {"content": "꼭짓점의 의미를 설명해 보세요.", "modelAnswer": "대칭축 위에서 최솟값(최댓값)을 갖는 점",
                  "gradingPoints": ["최솟값/최댓값", "대칭축"], "essayText": "그래프가 가장 낮은 점이에요."},
    })
    results.append(run("피드백", lambda: services.make_feedback(fb), 1, args.price_in, args.price_out, args.krw))

    rv = ReviewQuestionsRequest(subjectName="수학", studyLevel="HIGH", content=sample_text(2000),
                                weakTopics=["판별식과 x절편 개수", "평행이동", "범위 최솟값"], count=3)
    results.append(run("복습 출제", lambda: services.make_review_questions(rv), 1,
                       args.price_in, args.price_out, args.krw))

    print("\n===== 요약 (팀 공유용) =====")
    print(f"모델 {settings.AI_MODEL}")
    for r in results:
        verdict = "OK" if r["max"] <= 45 else "⚠️ 45초 초과"
        print(f"- {r['label']}: 평균 {r['avg']:.1f}초 / 최대 {r['max']:.1f}초 ({verdict}), "
              f"1회 약 {r['cost'] * args.krw:,.1f}원, 실패 {r['fails']}번")
    per_cycle = sum(r["cost"] for r in results[-3:]) * args.krw
    print(f"- 공부 1사이클(출제+피드백+복습) 약 {per_cycle:,.1f}원 → 1,000사이클 약 {per_cycle * 1000:,.0f}원")


if __name__ == "__main__":
    main()

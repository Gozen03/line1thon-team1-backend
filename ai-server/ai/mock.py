from .schemas import LLMFeedback, LLMQuestion, LLMQuestionSet, LLMReviewSet, LLMSummary

_MC = [
    ("EASY", "이차함수의 정의", "다음 중 이차함수인 것은?", ["y = x² + 1", "y = 2x + 1", "y = 1/x", "y = 3"],
     "x의 최고차항이 2차인 함수가 이차함수다."),
    ("EASY", "포물선의 볼록 방향", "y = −2x² 의 그래프는 어느 쪽으로 볼록한가?", ["위로 볼록", "아래로 볼록", "직선", "알 수 없다"],
     "x²의 계수가 음수이면 위로 볼록하다."),
    ("NORMAL", "꼭짓점 좌표 구하기", "이차함수 y = x² − 4x + 3 의 꼭짓점 좌표는?", ["(2, −1)", "(−2, 1)", "(2, 1)", "(4, 3)"],
     "y = (x − 2)² − 1 이므로 꼭짓점은 (2, −1)이다."),
    ("NORMAL", "판별식과 x절편 개수", "y = x² − 2x + 5 의 그래프와 x축의 교점 개수는?", ["0개", "1개", "2개", "3개"],
     "판별식 D = 4 − 20 < 0 이므로 만나지 않는다."),
    ("HARD", "그래프 평행이동", "y = x² 을 x축으로 3, y축으로 −2만큼 평행이동한 식은?",
     ["y = (x − 3)² − 2", "y = (x + 3)² − 2", "y = (x − 3)² + 2", "y = (x + 3)² + 2"],
     "x 대신 x − 3, y 대신 y + 2를 넣으면 y = (x − 3)² − 2다."),
    ("HARD", "범위가 정해진 최댓값·최솟값", "0 ≤ x ≤ 3 에서 y = x² − 2x 의 최솟값은?", ["−1", "0", "3", "−2"],
     "꼭짓점 x = 1이 범위 안에 있으므로 최솟값은 y(1) = −1이다."),
]


def _mc(number, difficulty, topic, content, options, explanation) -> LLMQuestion:
    return LLMQuestion(number=number, difficulty=difficulty, questionType="MULTIPLE_CHOICE", topic=topic,
                       content=content, options=list(options), answerIndex=0, explanation=explanation)


def question_set(req) -> LLMQuestionSet:
    questions = [_mc(i, *row) for i, row in enumerate(_MC, start=1)]
    questions.append(LLMQuestion(
        number=7, difficulty="ESSAY", questionType="ESSAY", topic="꼭짓점의 의미",
        content="이차함수 그래프에서 꼭짓점이 갖는 의미를 설명해 보세요.",
        modelAnswer="꼭짓점은 대칭축 위의 점이며, 이차함수가 최솟값(또는 최댓값)을 갖는 점이다.",
        gradingPoints=["최솟값/최댓값이 되는 점", "대칭축 위의 점"],
    ))
    extracted = "(Mock) 이차함수 y = ax² + bx + c 의 그래프는 포물선이다. 꼭짓점은 ..." if req.inputType == "IMAGE" else None
    return LLMQuestionSet(readable=True, enoughContent=True, extractedText=extracted, questions=questions)


def review_set(req) -> LLMReviewSet:
    rows = list(_MC[2:])
    questions = []
    for i in range(3):
        topic = req.weakTopics[i % len(req.weakTopics)]
        difficulty, _, content, options, explanation = rows[i]
        questions.append(_mc(i + 1, difficulty, topic, f"(Mock 복습 {i + 1}) {content}", options, explanation))
    return LLMReviewSet(questions=questions)


def feedback(req) -> LLMFeedback:
    weak = next((r.topic for r in req.results if not r.isCorrect), None)
    summary = "(Mock) 기본은 잘 잡혔어요." + (f" {weak} 부분만 더 다지면 초록잎이 될 수 있어요." if weak else "")
    return LLMFeedback(summary=summary, essayFeedback="(Mock) 핵심을 잘 설명했어요. 대칭축까지 함께 쓰면 더 좋아요.")


def review_summary(req, studied: str, reviewed: str) -> LLMSummary:
    return LLMSummary(summary=f"(Mock) 처음엔 부족한 부분이 있었지만, {reviewed} 복습에서 "
                              f"{req.gradedCount}문제를 모두 맞혀 잘 이해했어요.")

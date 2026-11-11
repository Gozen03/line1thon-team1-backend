import json

LEVEL_GUIDE = {
    "ELEMENTARY": "초등학교 교육과정 수준. 쉬운 낱말과 짧은 문장을 쓴다.",
    "MIDDLE": "중학교 교육과정 수준의 용어와 난이도.",
    "HIGH": "고등학교 교육과정 수준의 용어와 난이도.",
    "UNIVERSITY": "대학교 전공 기초 수준의 용어와 난이도.",
    "SELF_STUDY": "공부 내용과 메모에 드러난 수준에 맞춘다.",
}

COMMON_RULES = """\
- 모든 글은 한국어로 쓴다.
- 공부 내용과 메모에 있는 개념으로만 문제를 낸다. 내용에 없는 개념은 쓰지 않는다.
- topic은 그 문제가 확인하는 개념을 짧은 명사구로 쓴다 (예: "판별식과 x절편 개수"). 100자 이내.
- 객관식은 보기 4개. 정답은 하나뿐이고, 오답 보기는 학생이 실제로 헷갈릴 만한 값으로 만든다. 보기끼리 겹치지 않는다.
- answerIndex는 options 배열에서 정답의 위치(0~3)다.
- explanation은 정답인 이유를 1~2문장으로 쓴다.
- 수식은 일반 텍스트로 쓴다 (예: y = x² − 4x + 3). LaTeX는 쓰지 않는다.
"""


def questions_system(req) -> str:
    retry = ""
    if req.quizSetType == "RETRY":
        retry = (
            "\n[재도전]\n이 학생은 처음 풀이에서 0개를 맞혔다. 아래 약한 주제를 쉬움·보통 4문제 중 "
            "2문제 이상에 반영하고, 처음보다 차근차근 확인하는 문제로 만든다.\n"
            f"약한 주제: {json.dumps(req.weakTopics, ensure_ascii=False)}\n"
        )
    return f"""\
너는 학생이 공부한 내용으로 복습 문제를 만드는 선생님이다.

[과목] {req.subjectName}
[수준] {LEVEL_GUIDE[req.studyLevel]}

[만들 것] 정확히 7문제
- 1·2번 EASY: 개념을 확인하는 객관식
- 3·4번 NORMAL: 한 단계 적용하는 객관식
- 5·6번 HARD: 두 개념을 엮거나 함정이 있는 객관식
- 7번 ESSAY: 핵심 개념을 설명하게 하는 서술형. options·answerIndex·explanation은 null,
  modelAnswer(모범답안 1~3문장)와 gradingPoints(채점 포인트 2~3개)를 쓴다.
- 객관식은 questionType "MULTIPLE_CHOICE", 서술형은 "ESSAY". modelAnswer·gradingPoints는 null.
{COMMON_RULES}
[판단]
- 사진이 흐리거나 공부 내용이 아니라서 읽을 수 없으면 readable=false, questions는 빈 배열.
- enoughContent는 거의 항상 true다. 개념이 1~2개뿐이어도 정의·적용·계산·비교처럼 다른 각도로 물으면 7문제를 만들 수 있다.
  공부 내용이 한두 문장(약 50자 미만)뿐이거나 공부와 관계없는 글일 때만 false로 하고 questions는 빈 배열로 둔다.
- 사진을 읽었으면 extractedText에 사진 속 공부 내용을 빠짐없이 옮겨 적는다. 텍스트 입력이면 null.
{retry}"""


def questions_user(req, excluded: list[str]) -> str:
    parts = []
    if req.inputType == "TEXT":
        parts.append(f"[공부 내용]\n{req.content}")
    else:
        parts.append("[공부 내용] 위 노트 사진들 (순서대로)")
    if req.memo:
        parts.append(f"[메모 — 출제 중점, 3문제 이상에 반영]\n{req.memo}")
    if excluded:
        parts.append("[이미 낸 문제 — 다시 내지 말 것]\n" + "\n".join(f"- {e}" for e in excluded))
    return "\n\n".join(parts)


def feedback_system(req) -> str:
    return f"""\
너는 학생을 격려하는 선생님이다. 채점은 이미 끝났고, 너는 글만 쓴다. 점수는 매기지 않는다.

[과목] {req.subjectName}
[수준] {LEVEL_GUIDE[req.studyLevel]}
[결과] 객관식 {req.gradedCount}문제 중 {req.correctCount}개 정답, 잎 등급 {req.grade}

- summary: 1~2문장, 해요체, 150자 이내. 잘한 점을 먼저 말하고 더 공부할 주제를 짚는다.
  등급이 BUD(0개)면 실망하지 않게 다시 도전을 권한다.
- essayFeedback: 2~3문장, 해요체, 250자 이내. 채점 포인트를 기준으로 잘한 점 1개와 보탤 점 1개.
"""


def feedback_user(req) -> str:
    results = [{"topic": r.topic, "difficulty": r.difficulty, "isCorrect": r.isCorrect} for r in req.results]
    e = req.essay
    return (
        f"[문제별 결과]\n{json.dumps(results, ensure_ascii=False)}\n\n"
        f"[서술형 문제]\n{e.content}\n\n[모범답안]\n{e.modelAnswer}\n\n"
        f"[채점 포인트]\n{json.dumps(e.gradingPoints, ensure_ascii=False)}\n\n"
        f"[학생 답]\n{e.essayText or '(답을 적지 않음)'}"
    )


def review_system(req, topics_hint: str) -> str:
    return f"""\
너는 학생이 틀렸던 부분을 다시 확인하는 복습 문제를 만드는 선생님이다.

[과목] {req.subjectName}
[수준] {LEVEL_GUIDE[req.studyLevel]}

[만들 것] 객관식 정확히 3문제 (서술형 없음)
- {topics_hint}
- 각 문제의 topic은 아래 '약한 주제' 목록의 글자를 그대로 복사해 쓴다.
- 틀렸던 문제를 그대로 내지 말고 숫자·상황을 바꾼 새 문제로 낸다. 난이도는 틀렸던 문제와 비슷하게.
- difficulty는 EASY·NORMAL·HARD 중 하나, questionType은 "MULTIPLE_CHOICE",
  modelAnswer·gradingPoints는 null.
{COMMON_RULES}"""


def review_user(req) -> str:
    wrong = [w.model_dump() for w in req.wrongQuestions]
    parts = [
        f"[약한 주제]\n{json.dumps(req.weakTopics, ensure_ascii=False)}",
        f"[틀렸던 문제]\n{json.dumps(wrong, ensure_ascii=False)}",
        f"[원래 공부 내용]\n{req.content}",
    ]
    if req.memo:
        parts.append(f"[메모]\n{req.memo}")
    if req.excludeQuestions:
        parts.append("[이미 낸 복습 문제 — 다시 내지 말 것]\n" + "\n".join(f"- {e}" for e in req.excludeQuestions))
    return "\n\n".join(parts)


def review_feedback_system() -> str:
    return """\
너는 학생을 격려하는 선생님이다. 학생이 복습 문제를 모두 맞혔다.
summary를 1~2문장, 해요체, 150자 이내로 쓴다.
'처음엔 무엇이 부족했지만, (복습한 날) 복습에서 무엇을 맞혀 잘 이해했다'는 흐름으로 쓴다.
날짜는 주어진 글자 그대로 쓴다.
"""


def review_feedback_user(req, studied: str, reviewed: str) -> str:
    results = [r.model_dump() for r in req.results]
    return (
        f"[과목] {req.subjectName}\n[공부한 날] {studied}\n[복습한 날] {reviewed}\n"
        f"[복습 결과] {req.gradedCount}문제 중 {req.correctCount}개 정답\n"
        f"[문제별 결과] {json.dumps(results, ensure_ascii=False)}\n"
        f"[처음 총평] {req.originalSummary or '(없음)'}"
    )

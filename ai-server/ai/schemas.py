"""요청 형식(메인 서버 → AI 서버)과 모델 출력 형식."""
from datetime import datetime
from typing import Literal, Optional

from pydantic import BaseModel, ConfigDict, Field, model_validator

StudyLevel = Literal["ELEMENTARY", "MIDDLE", "HIGH", "UNIVERSITY", "SELF_STUDY"]
Difficulty = Literal["EASY", "NORMAL", "HARD", "ESSAY"]
Grade = Literal["GREEN", "LIGHT_GREEN", "AUTUMN", "BROWN", "BUD"]


class _Req(BaseModel):
    model_config = ConfigDict(extra="ignore", str_strip_whitespace=True)


# ---------- AI1 문제 출제 ----------
class QuestionsRequest(_Req):
    quizSetType: Literal["INITIAL", "RETRY"]
    subjectName: str = Field(min_length=1, max_length=30)
    studyLevel: StudyLevel
    inputType: Literal["IMAGE", "TEXT"]
    imageUrls: list[str] = Field(default_factory=list, max_length=5)
    content: Optional[str] = Field(default=None, max_length=20000)
    memo: Optional[str] = Field(default=None, max_length=200)
    weakTopics: list[str] = Field(default_factory=list, max_length=20)
    excludeQuestions: list[str] = Field(default_factory=list, max_length=50)

    @model_validator(mode="after")
    def _check_input(self):
        if self.inputType == "IMAGE":
            if not self.imageUrls:
                raise ValueError("inputType이 IMAGE면 imageUrls가 1개 이상 필요해요.")
            if self.quizSetType == "RETRY":
                raise ValueError("재도전(RETRY)은 inputType이 TEXT여야 해요.")
        if self.inputType == "TEXT" and not self.content:
            raise ValueError("inputType이 TEXT면 content가 필요해요.")
        return self


# ---------- AI2 피드백 ----------
class ResultItem(_Req):
    number: int
    topic: str
    difficulty: Difficulty
    isCorrect: bool


class EssayItem(_Req):
    content: str
    modelAnswer: str
    gradingPoints: list[str] = Field(default_factory=list)
    essayText: Optional[str] = Field(default=None, max_length=1000)


class FeedbackRequest(_Req):
    subjectName: str
    studyLevel: StudyLevel
    correctCount: int = Field(ge=0)
    gradedCount: int = Field(ge=1)
    grade: Grade
    results: list[ResultItem]
    essay: EssayItem


# ---------- AI3 복습 출제 ----------
class WrongQuestion(_Req):
    topic: str
    content: str
    options: Optional[list[str]] = None
    answerIndex: Optional[int] = None
    selectedIndex: Optional[int] = None


class ReviewQuestionsRequest(_Req):
    subjectName: str
    studyLevel: StudyLevel
    content: str = Field(min_length=1, max_length=20000)
    memo: Optional[str] = Field(default=None, max_length=200)
    weakTopics: list[str] = Field(min_length=1, max_length=20)
    wrongQuestions: list[WrongQuestion] = Field(default_factory=list)
    excludeQuestions: list[str] = Field(default_factory=list, max_length=50)
    count: Literal[3] = 3


# ---------- AI4 복습 피드백 ----------
class TopicResult(_Req):
    topic: str
    isCorrect: bool


class ReviewFeedbackRequest(_Req):
    subjectName: str
    originalSummary: Optional[str] = None
    studiedAt: datetime
    reviewedAt: datetime
    passed: bool = True
    correctCount: int = Field(ge=0)
    gradedCount: int = Field(ge=1)
    results: list[TopicResult]


# ---------- 모델(Gemini) 출력 형식 ----------
# 모델에게는 단순한 타입만 주고, 세부 규칙은 validation.py에서 검사한다.
class LLMQuestion(BaseModel):
    number: int
    difficulty: str
    questionType: str
    topic: str
    content: str
    options: Optional[list[str]] = None
    answerIndex: Optional[int] = None
    explanation: Optional[str] = None
    modelAnswer: Optional[str] = None
    gradingPoints: Optional[list[str]] = None


class LLMQuestionSet(BaseModel):
    readable: bool
    enoughContent: bool
    extractedText: Optional[str] = None
    questions: list[LLMQuestion]


class LLMReviewSet(BaseModel):
    questions: list[LLMQuestion]


class LLMFeedback(BaseModel):
    summary: str
    essayFeedback: str


class LLMSummary(BaseModel):
    summary: str

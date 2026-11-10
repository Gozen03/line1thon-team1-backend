class AIError(Exception):
    def __init__(self, code: str, status: int, message: str):
        super().__init__(message)
        self.code = code
        self.status = status
        self.message = message


class InvalidOutput(AIError):
    """모델 응답이 JSON 형식이 아님. 시간이 남으면 다시 요청할 수 있다."""

    def __init__(self, message: str = "AI 응답이 JSON 형식이 아니었어요."):
        super().__init__("AI_GENERATION_FAILED", 502, message)


def invalid_request(message: str = "요청 형식이 올바르지 않아요.") -> AIError:
    return AIError("INVALID_REQUEST", 400, message)


def unauthorized() -> AIError:
    return AIError("UNAUTHORIZED", 401, "내부 키가 없거나 틀렸어요.")


def image_fetch_failed(message: str = "사진을 내려받지 못했어요.") -> AIError:
    return AIError("IMAGE_FETCH_FAILED", 400, message)


def note_unreadable() -> AIError:
    return AIError("NOTE_UNREADABLE", 422, "노트 사진에서 공부 내용을 읽지 못했어요.")


def content_too_short() -> AIError:
    return AIError("CONTENT_TOO_SHORT", 422, "문제를 만들기엔 공부 내용이 너무 적어요.")


def generation_failed(message: str = "AI가 올바른 결과를 만들지 못했어요.") -> AIError:
    return AIError("AI_GENERATION_FAILED", 502, message)


def ai_timeout() -> AIError:
    return AIError("AI_TIMEOUT", 504, "AI 응답 시간이 초과됐어요.")


# 테스트용으로 일부러 낼 수 있는 에러 (Mock 모드에서 MOCK_ERROR:코드)
MOCKABLE = {
    "NOTE_UNREADABLE": note_unreadable,
    "CONTENT_TOO_SHORT": content_too_short,
    "AI_GENERATION_FAILED": generation_failed,
    "AI_TIMEOUT": ai_timeout,
    "IMAGE_FETCH_FAILED": image_fetch_failed,
}

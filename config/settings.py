"""AI 서버 설정.

DB·관리자 화면·로그인은 쓰지 않는다. 모든 비밀값은 환경변수로만 읽는다.
"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent


def env_bool(name: str, default: bool = False) -> bool:
    return os.environ.get(name, str(default)).strip().lower() in ("1", "true", "yes", "on")


SECRET_KEY = os.environ.get("DJANGO_SECRET_KEY", "dev-only-not-secret")
DEBUG = env_bool("DJANGO_DEBUG", False)

# "ai"는 Docker Compose 안에서 메인 서버가 부르는 서비스 이름
ALLOWED_HOSTS = ["ai", "localhost", "127.0.0.1"] + [
    h.strip() for h in os.environ.get("DJANGO_ALLOWED_HOSTS", "").split(",") if h.strip()
]

INSTALLED_APPS = ["ai"]
MIDDLEWARE = ["django.middleware.common.CommonMiddleware"]
ROOT_URLCONF = "config.urls"
WSGI_APPLICATION = "config.wsgi.application"
APPEND_SLASH = False

DATABASES = {}  # AI 서버는 DB를 쓰지 않음

LANGUAGE_CODE = "ko-kr"
TIME_ZONE = "Asia/Seoul"
USE_TZ = True

LOGGING = {
    "version": 1,
    "disable_existing_loggers": False,
    "formatters": {"plain": {"format": "%(asctime)s %(levelname)s %(name)s %(message)s"}},
    "handlers": {"console": {"class": "logging.StreamHandler", "formatter": "plain"}},
    "root": {"handlers": ["console"], "level": os.environ.get("LOG_LEVEL", "INFO")},
}

# ---- AI 서버 설정 ----
AI_INTERNAL_KEY = os.environ.get("AI_INTERNAL_KEY", "")

# 어느 회사 모델을 쓸지: openai(기본) 또는 gemini(예비)
AI_PROVIDER = os.environ.get("AI_PROVIDER", "openai").strip().lower()
_DEFAULT_MODEL = {"openai": "gpt-6-luna", "gemini": "gemini-2.5-flash"}
AI_MODEL = os.environ.get("AI_MODEL") or _DEFAULT_MODEL.get(AI_PROVIDER, "gpt-6-luna")

# OpenAI
OPENAI_API_KEY = os.environ.get("OPENAI_API_KEY", "")
# 추론 강도. none이면 추론을 꺼서 빠름. 빈 값이면 모델 기본값.
AI_REASONING_EFFORT = os.environ.get("AI_REASONING_EFFORT", "none").strip()

# Gemini (예비)
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY", "")
# 생각(thinking) 토큰 예산. 0이면 끔(빠름). 빈 값이면 모델 기본값.
AI_THINKING_BUDGET = os.environ.get("AI_THINKING_BUDGET", "0")
# true면 Gemini를 부르지 않고 예시 응답을 돌려줌 (키 없이 개발·메인 서버 연동 테스트용)
AI_MOCK = env_bool("AI_MOCK", False)

AI_TOTAL_BUDGET_SECONDS = 45  # 메인 서버 타임아웃(50초)보다 짧게
AI_RETRY_IF_ELAPSED_UNDER = 20  # 첫 호출이 이 시간 안에 끝났을 때만 재요청
AI_IMAGE_TIMEOUT_SECONDS = 8
AI_IMAGE_MAX_BYTES = 10 * 1024 * 1024
AI_IMAGE_MAX_SIDE = 1568

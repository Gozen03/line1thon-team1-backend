import base64
import json
import logging
import time
from functools import lru_cache

from django.conf import settings
from pydantic import BaseModel, ValidationError

from .errors import AIError, InvalidOutput, ai_timeout, generation_failed

log = logging.getLogger(__name__)
USAGE_LOG: list[dict] = []


def generate_json(system: str, user_text: str, schema: type[BaseModel], *,
                  images: list[bytes] | None = None, timeout_s: float = 30) -> BaseModel:
    """지시문 + (사진들) + 본문을 보내고, schema 형식의 JSON을 받아 돌려준다."""
    provider = settings.AI_PROVIDER
    started = time.monotonic()
    try:
        if provider == "openai":
            result, usage = _openai(system, user_text, schema, images or [], timeout_s)
        elif provider == "gemini":
            result, usage = _gemini(system, user_text, schema, images or [], timeout_s)
        else:
            raise generation_failed(f"AI_PROVIDER 값이 올바르지 않아요: {provider}")
    finally:
        log.info("%s call %.1fs model=%s", provider, time.monotonic() - started, settings.AI_MODEL)

    if usage:
        usage["seconds"] = round(time.monotonic() - started, 1)
        USAGE_LOG.append(usage)
        log.info("tokens in=%s out=%s reasoning=%s", usage["input"], usage["output"], usage["thinking"])
    return result


# ---------------- OpenAI (GPT-6 Luna) ----------------
@lru_cache(maxsize=1)
def _openai_client():
    from openai import OpenAI

    if not settings.OPENAI_API_KEY:
        raise generation_failed("OPENAI_API_KEY가 설정되지 않았어요.")
    # 재시도는 services.py가 시간 예산 안에서 직접 하므로 SDK 자동 재시도는 끈다
    return OpenAI(api_key=settings.OPENAI_API_KEY, max_retries=0)


def _openai(system, user_text, schema, images, timeout_s):
    import openai

    content = [
        {"type": "input_image", "image_url": "data:image/jpeg;base64," + base64.b64encode(img).decode(),
         "detail": "high"}  # 노트 글씨를 읽어야 해서 고해상도
        for img in images
    ]
    content.append({"type": "input_text", "text": user_text})  # 사진을 먼저, 글을 뒤에

    kwargs = dict(
        model=settings.AI_MODEL,
        instructions=system,
        input=[{"role": "user", "content": content}],
        text_format=schema,
        timeout=max(1.0, timeout_s),
    )
    if settings.AI_REASONING_EFFORT:
        kwargs["reasoning"] = {"effort": settings.AI_REASONING_EFFORT}

    try:
        resp = _openai_client().responses.parse(**kwargs)
    except AIError:
        raise
    except openai.APITimeoutError as exc:
        raise ai_timeout() from exc
    except openai.RateLimitError as exc:
        raise generation_failed("OpenAI 요청 한도를 넘었거나 크레딧이 부족해요.") from exc
    except openai.APIStatusError as exc:
        log.warning("openai api error status=%s body=%s", exc.status_code, str(exc)[:300])
        raise generation_failed() from exc
    except openai.APIConnectionError as exc:
        log.warning("openai connection error: %s", type(exc).__name__)
        raise generation_failed("OpenAI 서버에 연결하지 못했어요.") from exc
    except (ValidationError, json.JSONDecodeError) as exc:
        raise InvalidOutput() from exc

    usage = None
    if resp.usage:
        details = getattr(resp.usage, "output_tokens_details", None)
        usage = {
            "input": resp.usage.input_tokens or 0,
            "output": resp.usage.output_tokens or 0,  # 추론 토큰이 이미 포함된 값
            "thinking": 0,
            "reasoning_included": getattr(details, "reasoning_tokens", 0) or 0,
        }

    parsed = resp.output_parsed
    if parsed is None:
        raise InvalidOutput("AI가 형식에 맞는 답을 주지 않았어요(거절 또는 빈 응답).")
    return parsed, usage
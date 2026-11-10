"""API 입구. 내부 키 검사 → 요청 형식 검사 → 처리 → { success, data, error } 로 감싸기."""
import hmac
import json
import logging
import time
import uuid
from functools import wraps

from django.conf import settings
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from django.views.decorators.http import require_GET
from pydantic import ValidationError

from . import services
from .errors import AIError, invalid_request, unauthorized
from .schemas import FeedbackRequest, QuestionsRequest, ReviewFeedbackRequest, ReviewQuestionsRequest

log = logging.getLogger(__name__)
VERSION = "1.0.0"
JSON_DUMPS = {"ensure_ascii": False}


def ok(data, status=200):
    return JsonResponse({"success": True, "data": data, "error": None}, status=status, json_dumps_params=JSON_DUMPS)


def fail(err: AIError):
    body = {"success": False, "data": None, "error": {"code": err.code, "message": err.message}}
    return JsonResponse(body, status=err.status, json_dumps_params=JSON_DUMPS)


def _check_key(request) -> None:
    expected = settings.AI_INTERNAL_KEY
    if not expected:
        return
    given = request.headers.get("X-Internal-Key", "")
    if not hmac.compare_digest(given.encode(), expected.encode()):
        raise unauthorized()


def ai_endpoint(schema):
    """POST JSON 엔드포인트 공통 처리."""

    def decorator(handler):
        @csrf_exempt
        @wraps(handler)
        def view(request):
            rid = uuid.uuid4().hex[:8]
            started = time.monotonic()
            try:
                if request.method != "POST":
                    raise invalid_request("POST로 요청해 주세요.")
                _check_key(request)
                try:
                    payload = json.loads(request.body or b"{}")
                    req = schema.model_validate(payload)
                except json.JSONDecodeError:
                    raise invalid_request("본문이 JSON이 아니에요.")
                except ValidationError as exc:
                    detail = "; ".join(
                        f"{'.'.join(str(p) for p in e['loc']) or '본문'}: {e['msg']}" for e in exc.errors()[:5]
                    )
                    raise invalid_request(f"요청 형식 오류 — {detail}")
                response = ok(handler(req))
            except AIError as err:
                response = fail(err)
            except Exception:
                log.exception("[%s] unexpected error", rid)
                response = fail(AIError("INTERNAL_ERROR", 500, "AI 서버 내부 오류예요."))
            log.info("[%s] %s %s -> %s %.1fs", rid, request.method, request.path,
                     response.status_code, time.monotonic() - started)
            return response

        return view

    return decorator


@require_GET
def health(request):
    return JsonResponse({"status": "ok", "version": VERSION, "mock": settings.AI_MOCK,
                         "provider": settings.AI_PROVIDER, "model": settings.AI_MODEL})


@ai_endpoint(QuestionsRequest)
def questions(req):
    return services.make_questions(req)


@ai_endpoint(FeedbackRequest)
def feedback(req):
    return services.make_feedback(req)


@ai_endpoint(ReviewQuestionsRequest)
def review_questions(req):
    return services.make_review_questions(req)


@ai_endpoint(ReviewFeedbackRequest)
def review_feedback(req):
    return services.make_review_feedback(req)

import io
import json
import random
from collections import Counter
from unittest import mock as umock

from django.test import SimpleTestCase, override_settings
from PIL import Image

from . import mock as ai_mock
from .errors import image_fetch_failed
from .images import to_jpeg
from .schemas import LLMQuestion, LLMQuestionSet, QuestionsRequest
from .validation import check_initial, check_review, clip, shuffle_options

KEY = "test-key"
H = {"HTTP_X_INTERNAL_KEY": KEY}

TEXT_REQ = {
    "quizSetType": "INITIAL", "subjectName": "수학", "studyLevel": "HIGH", "inputType": "TEXT",
    "content": "이차함수의 그래프와 꼭짓점, 판별식에 대해 공부했다. " * 3, "memo": "꼭짓점",
}
FEEDBACK_REQ = {
    "subjectName": "수학", "studyLevel": "HIGH", "correctCount": 3, "gradedCount": 6, "grade": "AUTUMN",
    "results": [{"number": i, "topic": f"주제{i}", "difficulty": "EASY", "isCorrect": i % 2 == 0} for i in range(1, 7)],
    "essay": {"content": "꼭짓점의 의미는?", "modelAnswer": "최솟값이 되는 점", "gradingPoints": ["최솟값", "대칭축"],
              "essayText": "가장 낮은 점"},
}
REVIEW_REQ = {
    "subjectName": "수학", "studyLevel": "HIGH", "content": "이차함수 공부 내용",
    "weakTopics": ["판별식과 x절편 개수", "그래프 평행이동", "범위가 정해진 최댓값·최솟값"],
    "wrongQuestions": [{"topic": "판별식과 x절편 개수", "content": "y = x² − 2x + 5 의 x절편 개수는?",
                        "options": ["0개", "1개", "2개", "3개"], "answerIndex": 0, "selectedIndex": 2}],
    "count": 3,
}
REVIEW_FB_REQ = {
    "subjectName": "수학", "originalSummary": "판별식이 부족해요.", "studiedAt": "2026-10-29T20:00:00+09:00",
    "reviewedAt": "2026-11-01T19:40:00+09:00", "passed": True, "correctCount": 3, "gradedCount": 3,
    "results": [{"topic": "판별식과 x절편 개수", "isCorrect": True}],
}


@override_settings(AI_MOCK=True, AI_INTERNAL_KEY=KEY)
class ApiTests(SimpleTestCase):
    def post(self, path, body, **headers):
        return self.client.post(path, data=json.dumps(body), content_type="application/json", **headers)

    def test_health_without_key(self):
        res = self.client.get("/health")
        self.assertEqual(res.status_code, 200)
        self.assertEqual(res.json()["status"], "ok")

    def test_missing_or_wrong_key(self):
        self.assertEqual(self.post("/ai/v1/questions", TEXT_REQ).status_code, 401)
        res = self.post("/ai/v1/questions", TEXT_REQ, HTTP_X_INTERNAL_KEY="wrong")
        self.assertEqual(res.json()["error"]["code"], "UNAUTHORIZED")

    def test_invalid_body(self):
        res = self.post("/ai/v1/questions", {"quizSetType": "INITIAL"}, **H)
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json()["error"]["code"], "INVALID_REQUEST")

    def test_retry_must_be_text(self):
        body = dict(TEXT_REQ, quizSetType="RETRY", inputType="IMAGE", imageUrls=["https://x/1.jpg"])
        self.assertEqual(self.post("/ai/v1/questions", body, **H).status_code, 400)

    def test_questions_text(self):
        res = self.post("/ai/v1/questions", TEXT_REQ, **H)
        self.assertEqual(res.status_code, 200)
        body = res.json()
        self.assertTrue(body["success"])
        data = body["data"]
        self.assertIsNone(data["extractedText"])
        qs = data["questions"]
        self.assertEqual([q["number"] for q in qs], [1, 2, 3, 4, 5, 6, 7])
        self.assertEqual([q["difficulty"] for q in qs], ["EASY", "EASY", "NORMAL", "NORMAL", "HARD", "HARD", "ESSAY"])
        for q in qs[:6]:
            self.assertEqual(q["questionType"], "MULTIPLE_CHOICE")
            self.assertEqual(len(set(q["options"])), 4)
            self.assertIn(q["answerIndex"], range(4))
            self.assertIsNone(q["modelAnswer"])
        essay = qs[6]
        self.assertEqual(essay["questionType"], "ESSAY")
        self.assertIsNone(essay["options"])
        self.assertTrue(essay["modelAnswer"])
        self.assertGreaterEqual(len(essay["gradingPoints"]), 2)

    def test_questions_image_mock_skips_download(self):
        body = dict(TEXT_REQ, inputType="IMAGE", content=None, imageUrls=["https://example.com/a.jpg"])
        res = self.post("/ai/v1/questions", body, **H)
        self.assertEqual(res.status_code, 200)
        self.assertTrue(res.json()["data"]["extractedText"])

    def test_mock_error_trigger(self):
        res = self.post("/ai/v1/questions", dict(TEXT_REQ, memo="MOCK_ERROR:NOTE_UNREADABLE"), **H)
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["error"]["code"], "NOTE_UNREADABLE")

    def test_feedback(self):
        data = self.post("/ai/v1/feedback", FEEDBACK_REQ, **H).json()["data"]
        self.assertTrue(data["summary"])
        self.assertTrue(data["essayFeedback"])

    def test_feedback_empty_essay_uses_fixed_text(self):
        body = json.loads(json.dumps(FEEDBACK_REQ))
        body["essay"]["essayText"] = "  "
        data = self.post("/ai/v1/feedback", body, **H).json()["data"]
        self.assertTrue(data["essayFeedback"].startswith("답을 적지 않았어요"))

    def test_review_questions(self):
        data = self.post("/ai/v1/review-questions", REVIEW_REQ, **H).json()["data"]
        self.assertEqual(len(data["questions"]), 3)
        for q in data["questions"]:
            self.assertIn(q["topic"], REVIEW_REQ["weakTopics"])

    def test_review_questions_one_topic_fills_three(self):
        body = dict(REVIEW_REQ, weakTopics=["그래프 평행이동"])
        data = self.post("/ai/v1/review-questions", body, **H).json()["data"]
        self.assertEqual([q["topic"] for q in data["questions"]], ["그래프 평행이동"] * 3)

    def test_review_feedback_date(self):
        data = self.post("/ai/v1/review-feedback", REVIEW_FB_REQ, **H).json()["data"]
        self.assertIn("11월 1일", data["summary"])


class ValidationTests(SimpleTestCase):
    def _qs(self):
        req = QuestionsRequest.model_validate(TEXT_REQ)
        return ai_mock.question_set(req).questions

    def test_wrong_distribution_detected(self):
        qs = self._qs()
        qs[0].difficulty = "HARD"
        _, problems = check_initial(qs)
        self.assertTrue(problems)

    def test_renumbers_by_difficulty(self):
        qs = self._qs()
        qs.reverse()
        for i, q in enumerate(qs, start=1):
            q.number = i
        fixed, problems = check_initial(qs)
        self.assertEqual(problems, [])
        self.assertEqual(fixed[0].difficulty, "EASY")
        self.assertEqual(fixed[6].difficulty, "ESSAY")

    def test_duplicate_options_detected(self):
        qs = self._qs()
        qs[2].options = ["1", "1", "2", "3"]
        _, problems = check_initial(qs)
        self.assertTrue(any("겹침" in p for p in problems))

    def test_long_topic_cut_to_100(self):
        qs = self._qs()
        qs[0].topic = "가" * 150
        fixed, problems = check_initial(qs)
        self.assertEqual(problems, [])
        self.assertEqual(len(fixed[0].topic), 100)

    def test_review_topic_spacing_normalized(self):
        qs = [LLMQuestion(number=i, difficulty="NORMAL", questionType="MULTIPLE_CHOICE", topic="그래프  평행이동",
                          content=f"문제{i}", options=["a", "b", "c", "d"], answerIndex=0, explanation="e")
              for i in (1, 2, 3)]
        fixed, problems = check_review(qs, ["그래프 평행이동"])
        self.assertEqual(problems, [])
        self.assertEqual(fixed[0].topic, "그래프 평행이동")

    def test_shuffle_keeps_correct_answer_and_limits_same_position(self):
        rng = random.Random(1)
        for _ in range(200):
            qs = self._qs()
            correct = [q.options[q.answerIndex] for q in qs[:6]]
            shuffle_options(qs, rng)
            self.assertEqual([q.options[q.answerIndex] for q in qs[:6]], correct)
            self.assertLessEqual(max(Counter(q.answerIndex for q in qs[:6]).values()), 3)

    def test_clip(self):
        self.assertEqual(clip("짧아요.", 200), "짧아요.")
        self.assertLessEqual(len(clip("가나다라요. " * 60, 200)), 201)


class ImageTests(SimpleTestCase):
    def test_png_with_alpha_becomes_small_jpeg(self):
        buf = io.BytesIO()
        Image.new("RGBA", (4000, 3000), (255, 0, 0, 128)).save(buf, format="PNG")
        out = Image.open(io.BytesIO(to_jpeg(buf.getvalue())))
        self.assertEqual(out.format, "JPEG")
        self.assertLessEqual(max(out.size), 1568)

    def test_heic_is_converted(self):
        import pillow_heif

        buf = io.BytesIO()
        try:
            pillow_heif.from_pillow(Image.new("RGB", (64, 48), "blue")).save(buf, format="HEIF")
        except Exception:
            self.skipTest("HEIC 인코더 없음")
        out = Image.open(io.BytesIO(to_jpeg(buf.getvalue())))
        self.assertEqual(out.format, "JPEG")

    def test_not_an_image(self):
        with self.assertRaises(Exception) as ctx:
            to_jpeg(b"hello")
        self.assertEqual(ctx.exception.code, "IMAGE_FETCH_FAILED")


@override_settings(AI_PROVIDER="openai", AI_MODEL="gpt-6-luna", OPENAI_API_KEY="fake", AI_REASONING_EFFORT="none")
class OpenAIAdapterTests(SimpleTestCase):
    """OpenAI 호출부: 요청을 올바르게 만드는지, 응답·에러를 올바르게 바꾸는지 (네트워크 없이)."""

    def setUp(self):
        from . import llm
        llm._openai_client.cache_clear()
        self.llm = llm

    def _fake_client(self, parse):
        client = umock.MagicMock()
        client.responses.parse.side_effect = parse
        return umock.patch.object(self.llm, "_openai_client", return_value=client), client

    def test_builds_request_and_parses(self):
        from .schemas import LLMSummary

        resp = umock.MagicMock()
        resp.output_parsed = LLMSummary(summary="좋아요")
        resp.usage.input_tokens, resp.usage.output_tokens = 1200, 80
        patcher, client = self._fake_client(lambda **kw: resp)
        with patcher:
            out = self.llm.generate_json("지시", "본문", LLMSummary, images=[b"\xff\xd8jpeg"], timeout_s=20)
        self.assertEqual(out.summary, "좋아요")
        kw = client.responses.parse.call_args.kwargs
        self.assertEqual(kw["model"], "gpt-6-luna")
        self.assertEqual(kw["instructions"], "지시")
        self.assertIs(kw["text_format"], LLMSummary)
        self.assertEqual(kw["reasoning"], {"effort": "none"})
        self.assertEqual(kw["timeout"], 20)
        content = kw["input"][0]["content"]
        self.assertEqual(content[0]["type"], "input_image")
        self.assertTrue(content[0]["image_url"].startswith("data:image/jpeg;base64,"))
        self.assertEqual(content[-1], {"type": "input_text", "text": "본문"})
        self.assertEqual(self.llm.USAGE_LOG[-1]["input"], 1200)

    def test_refusal_is_invalid_output(self):
        from .errors import InvalidOutput
        from .schemas import LLMSummary

        resp = umock.MagicMock()
        resp.output_parsed = None
        patcher, _ = self._fake_client(lambda **kw: resp)
        with patcher, self.assertRaises(InvalidOutput):
            self.llm.generate_json("지시", "본문", LLMSummary)

    def test_error_mapping(self):
        import openai
        try:
            import httpx2 as httpx  # openai 최신판
        except ImportError:
            import httpx
        from .errors import AIError
        from .schemas import LLMSummary

        req = httpx.Request("POST", "https://api.openai.com/v1/responses")
        cases = [
            (openai.APITimeoutError(request=req), "AI_TIMEOUT"),
            (openai.RateLimitError("limit", response=httpx.Response(429, request=req), body=None),
             "AI_GENERATION_FAILED"),
            (openai.APIConnectionError(request=req), "AI_GENERATION_FAILED"),
        ]
        for exc, code in cases:
            def boom(**kw):
                raise exc
            patcher, _ = self._fake_client(boom)
            with patcher, self.assertRaises(AIError) as ctx:
                self.llm.generate_json("지시", "본문", LLMSummary)
            self.assertEqual(ctx.exception.code, code)

    def test_output_schemas_are_strict_compatible(self):
        from openai.lib._pydantic import to_strict_json_schema
        from .schemas import LLMFeedback, LLMQuestionSet, LLMReviewSet, LLMSummary

        for schema in (LLMQuestionSet, LLMReviewSet, LLMFeedback, LLMSummary):
            to_strict_json_schema(schema)


@override_settings(AI_MOCK=False, AI_INTERNAL_KEY=KEY, OPENAI_API_KEY="fake")
class RealPathTests(SimpleTestCase):
    """Gemini 호출 부분만 가짜로 바꿔서 재요청·에러 처리를 확인한다."""

    def post(self, path, body):
        return self.client.post(path, data=json.dumps(body), content_type="application/json", **H)

    def test_retry_once_after_bad_output(self):
        req = QuestionsRequest.model_validate(TEXT_REQ)
        good = ai_mock.question_set(req)
        bad = LLMQuestionSet(readable=True, enoughContent=True, questions=good.questions[:5])
        with umock.patch("ai.llm.generate_json", side_effect=[bad, good]) as gen:
            res = self.post("/ai/v1/questions", TEXT_REQ)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(gen.call_count, 2)
        self.assertIn("직전 출력의 문제", gen.call_args_list[1].args[1])

    def test_fails_after_two_bad_outputs(self):
        req = QuestionsRequest.model_validate(TEXT_REQ)
        bad = LLMQuestionSet(readable=True, enoughContent=True, questions=ai_mock.question_set(req).questions[:5])
        with umock.patch("ai.llm.generate_json", side_effect=[bad, bad]):
            res = self.post("/ai/v1/questions", TEXT_REQ)
        self.assertEqual(res.status_code, 502)
        self.assertEqual(res.json()["error"]["code"], "AI_GENERATION_FAILED")

    def test_unreadable_photo(self):
        body = dict(TEXT_REQ, inputType="IMAGE", content=None, imageUrls=["https://example.com/a.jpg"])
        bad = LLMQuestionSet(readable=False, enoughContent=False, questions=[])
        with umock.patch("ai.services.fetch_images", return_value=[b"jpg"]), \
                umock.patch("ai.llm.generate_json", return_value=bad):
            res = self.post("/ai/v1/questions", body)
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["error"]["code"], "NOTE_UNREADABLE")

    def test_image_fetch_error(self):
        body = dict(TEXT_REQ, inputType="IMAGE", content=None, imageUrls=["https://example.com/a.jpg"])
        with umock.patch("ai.services.fetch_images", side_effect=image_fetch_failed()):
            res = self.post("/ai/v1/questions", body)
        self.assertEqual(res.status_code, 400)
        self.assertEqual(res.json()["error"]["code"], "IMAGE_FETCH_FAILED")


@override_settings(AI_MOCK=False, AI_INTERNAL_KEY=KEY, OPENAI_API_KEY="fake")
class EnoughContentTests(SimpleTestCase):
    """모델이 충분한 내용에도 enoughContent=false를 줄 때 (11/10 재민 님 제보)."""

    LONG = ("이차함수 y = ax² + bx + c 의 꼭짓점은 완전제곱식으로 바꿔 구한다. "
            "판별식 D = b² − 4ac 의 부호로 x축과 만나는 점의 개수를 판단한다. ") * 3

    def post(self, body):
        return self.client.post("/ai/v1/questions", data=json.dumps(body), content_type="application/json", **H)

    def test_long_text_false_is_retried(self):
        body = dict(TEXT_REQ, content=self.LONG)
        good = ai_mock.question_set(QuestionsRequest.model_validate(body))
        bad = LLMQuestionSet(readable=True, enoughContent=False, questions=[])
        with umock.patch("ai.llm.generate_json", side_effect=[bad, good]) as gen:
            res = self.post(body)
        self.assertEqual(res.status_code, 200)
        self.assertEqual(gen.call_count, 2)
        self.assertIn("충분함", gen.call_args_list[1].args[1])

    def test_long_text_false_twice_is_retryable_error(self):
        body = dict(TEXT_REQ, content=self.LONG)
        bad = LLMQuestionSet(readable=True, enoughContent=False, questions=[])
        with umock.patch("ai.llm.generate_json", side_effect=[bad, bad]):
            res = self.post(body)
        self.assertEqual(res.status_code, 502)
        self.assertEqual(res.json()["error"]["code"], "AI_GENERATION_FAILED")

    def test_short_text_false_is_content_too_short(self):
        body = dict(TEXT_REQ, content="이차함수 꼭짓점을 공부했다.")
        bad = LLMQuestionSet(readable=True, enoughContent=False, questions=[])
        with umock.patch("ai.llm.generate_json", return_value=bad) as gen:
            res = self.post(body)
        self.assertEqual(res.status_code, 422)
        self.assertEqual(res.json()["error"]["code"], "CONTENT_TOO_SHORT")
        self.assertEqual(gen.call_count, 1)

package com.line1thon.jisiknamu.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

// 가짜 AI 서버로 HttpAiClient가 AI 서버 API 명세(AI1~AI4)대로 보내고 받는지 확인한다
class HttpAiClientTest {

    private static final String KEY = "test-internal-key";

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private FakeAiServer server;
    private HttpAiClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new FakeAiServer();
        client = new HttpAiClient(mapper, server.url() + "/", KEY, Duration.ofSeconds(2));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private QuizGenerateRequest imageQuizRequest() {
        return new QuizGenerateRequest(QuizSetType.INITIAL, "수학", StudyLevel.HIGH, InputType.IMAGE,
                List.of("https://bucket/a.jpg?X-Amz=1", "https://bucket/b.jpg"), null, "꼭짓점", List.of(), List.of());
    }

    private ReviewQuestionsRequest reviewRequest(List<String> weakTopics) {
        return new ReviewQuestionsRequest("수학", StudyLevel.HIGH, "공부 내용", null, weakTopics,
                List.of(new ReviewQuestionsRequest.WrongQuestion("주제 A", "틀린 문제", List.of("가", "나", "다", "라"), 0, null)),
                List.of(), 3);
    }

    // AI1

    @Test
    void AI1_경로_헤더_요청_본문이_명세대로다() {
        server.respondSuccess(FakeAiServer.quizData("읽은 내용"));

        client.generateQuiz(imageQuizRequest());

        FakeAiServer.Received received = server.last();
        assertThat(received.method()).isEqualTo("POST");
        assertThat(received.path()).isEqualTo("/ai/v1/questions");
        assertThat(received.internalKey()).isEqualTo(KEY);
        assertThat(received.contentType()).startsWith("application/json");
        JsonNode body = mapper.readTree(received.body());
        assertThat(body.get("quizSetType").asString()).isEqualTo("INITIAL");
        assertThat(body.get("subjectName").asString()).isEqualTo("수학");
        assertThat(body.get("studyLevel").asString()).isEqualTo("HIGH");
        assertThat(body.get("inputType").asString()).isEqualTo("IMAGE");
        assertThat(body.get("imageUrls")).hasSize(2);
        assertThat(body.get("imageUrls").get(0).asString()).isEqualTo("https://bucket/a.jpg?X-Amz=1");
        assertThat(body.has("content")).isTrue();
        assertThat(body.get("content").isNull()).isTrue();
        assertThat(body.get("memo").asString()).isEqualTo("꼭짓점");
        assertThat(body.get("weakTopics").isArray()).isTrue();
        assertThat(body.get("excludeQuestions").isArray()).isTrue();
    }

    @Test
    void AI1_응답을_문제로_바꾸고_모르는_필드는_무시한다() {
        server.respondSuccess(FakeAiServer.quizData("읽은 내용"));

        GeneratedQuiz quiz = client.generateQuiz(imageQuizRequest());

        assertThat(quiz.extractedText()).isEqualTo("읽은 내용");
        assertThat(quiz.questions()).hasSize(7);
        GeneratedQuestion first = quiz.questions().get(0);
        assertThat(first.difficulty()).isEqualTo(Difficulty.EASY);
        assertThat(first.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(first.options()).containsExactly("가", "나", "다", "라");
        assertThat(first.answerIndex()).isEqualTo(1);
        assertThat(first.explanation()).isEqualTo("해설 1");
        GeneratedQuestion essay = quiz.questions().get(6);
        assertThat(essay.questionType()).isEqualTo(QuestionType.ESSAY);
        assertThat(essay.options()).isNull();
        assertThat(essay.answerIndex()).isNull();
        assertThat(essay.modelAnswer()).isEqualTo("모범답안");
        assertThat(essay.gradingPoints()).containsExactly("포인트 1", "포인트 2");
    }

    @Test
    void AI1_readable이_false면_NOTE_UNREADABLE() {
        server.respondSuccess("{\"readable\":false,\"extractedText\":null,\"questions\":[]}");

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.NOTE_UNREADABLE);
    }

    @Test
    void AI1_문제가_7개가_아니면_저장하지_않고_AI_SERVER_ERROR() {
        String six = FakeAiServer.quizData(null).replaceFirst("\\{\"number\":1,[^}]*},", "");
        server.respondSuccess(six);

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void AI1_보기가_4개가_아니거나_정답_위치가_틀리면_AI_SERVER_ERROR() {
        server.respondSuccess(FakeAiServer.quizData(null).replaceFirst("\\[\"가\",\"나\",\"다\",\"라\"]", "[\"가\",\"나\"]"));
        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);

        server.respondSuccess(FakeAiServer.quizData(null).replaceFirst("\"answerIndex\":1", "\"answerIndex\":4"));
        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void AI1_topic이_100자를_넘으면_AI_SERVER_ERROR() {
        server.respondSuccess(FakeAiServer.quizData(null).replaceFirst("주제 1", "가".repeat(101)));

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void AI1_enum이_명세에_없는_값이면_AI_SERVER_ERROR() {
        server.respondSuccess(FakeAiServer.quizData(null).replaceFirst("\"EASY\"", "\"VERY_EASY\""));

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    // 에러 변환 (AI 서버 API 명세 1장)

    @ParameterizedTest
    @CsvSource({
            "400, INVALID_REQUEST, INTERNAL_ERROR",
            "401, UNAUTHORIZED, INTERNAL_ERROR",
            "400, IMAGE_FETCH_FAILED, AI_SERVER_ERROR",
            "422, NOTE_UNREADABLE, NOTE_UNREADABLE",
            "422, CONTENT_TOO_SHORT, CONTENT_TOO_SHORT",
            "502, AI_GENERATION_FAILED, AI_SERVER_ERROR",
            "504, AI_TIMEOUT, AI_TIMEOUT",
            "500, SOMETHING_NEW, AI_SERVER_ERROR"
    })
    void AI_서버_에러_코드를_메인_서버_에러로_바꾼다(int status, String aiCode, ErrorCode expected) {
        server.respondError(status, aiCode);

        assertError(() -> client.generateQuiz(imageQuizRequest()), expected);
    }

    @Test
    void JSON이_아닌_응답은_AI_SERVER_ERROR() {
        server.respondHtml(502);

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void 성공_200인데_success가_false면_에러_코드대로() {
        server.respond(200, "{\"success\":false,\"data\":null,\"error\":{\"code\":\"CONTENT_TOO_SHORT\",\"message\":\"m\"}}");

        assertError(() -> client.generateQuiz(imageQuizRequest()), ErrorCode.CONTENT_TOO_SHORT);
    }

    @Test
    void 응답이_늦으면_AI_TIMEOUT() {
        HttpAiClient fast = new HttpAiClient(mapper, server.url(), KEY, Duration.ofMillis(300));
        server.respondSuccess(FakeAiServer.quizData(null));
        server.delay(1500);

        assertError(() -> fast.generateQuiz(imageQuizRequest()), ErrorCode.AI_TIMEOUT);
    }

    @Test
    void AI_서버가_꺼져_있으면_AI_SERVER_ERROR() {
        String url = server.url();
        server.close();
        HttpAiClient down = new HttpAiClient(mapper, url, KEY, Duration.ofSeconds(2));

        assertError(() -> down.generateQuiz(imageQuizRequest()), ErrorCode.AI_SERVER_ERROR);
    }

    @Test
    void 내부_키가_비어_있으면_서버가_뜨지_않는다() {
        assertThatThrownBy(() -> new HttpAiClient(mapper, server.url(), " ", Duration.ofSeconds(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    // AI2

    @Test
    void AI2_요청과_응답() {
        server.respondSuccess("{\"summary\":\"총평\",\"essayFeedback\":\"피드백\"}");
        FeedbackRequest request = new FeedbackRequest("수학", StudyLevel.HIGH, 3, 6, LeafGrade.AUTUMN,
                List.of(new FeedbackRequest.QuestionResult(1, "주제 1", Difficulty.EASY, true)),
                new FeedbackRequest.Essay("설명해 보세요.", "모범답안", List.of("포인트"), "내 답"));

        FeedbackResult result = client.generateFeedback(request);

        assertThat(result).isEqualTo(new FeedbackResult("총평", "피드백"));
        assertThat(server.last().path()).isEqualTo("/ai/v1/feedback");
        JsonNode body = mapper.readTree(server.last().body());
        assertThat(body.get("grade").asString()).isEqualTo("AUTUMN");
        assertThat(body.get("correctCount").asInt()).isEqualTo(3);
        assertThat(body.get("gradedCount").asInt()).isEqualTo(6);
        assertThat(body.get("results").get(0).get("isCorrect").asBoolean()).isTrue();
        assertThat(body.get("results").get(0).get("difficulty").asString()).isEqualTo("EASY");
        assertThat(body.get("essay").get("essayText").asString()).isEqualTo("내 답");
        assertThat(body.get("essay").get("gradingPoints").get(0).asString()).isEqualTo("포인트");
    }

    @Test
    void AI2_summary가_비어_있으면_AI_SERVER_ERROR() {
        server.respondSuccess("{\"summary\":\"\",\"essayFeedback\":\"피드백\"}");
        FeedbackRequest request = new FeedbackRequest("수학", StudyLevel.HIGH, 3, 6, LeafGrade.AUTUMN, List.of(),
                new FeedbackRequest.Essay("문제", "모범답안", List.of("포인트"), ""));

        assertError(() -> client.generateFeedback(request), ErrorCode.AI_SERVER_ERROR);
    }

    // AI3

    @Test
    void AI3_요청과_응답() {
        server.respondSuccess("{\"questions\":[" + FakeAiServer.multipleChoice(1, "NORMAL", "주제 A") + ","
                + FakeAiServer.multipleChoice(2, "NORMAL", "주제 B") + ","
                + FakeAiServer.multipleChoice(3, "NORMAL", "주제 A") + "]}");

        ReviewQuestionsResult result = client.generateReviewQuestions(reviewRequest(List.of("주제 A", "주제 B")));

        assertThat(result.questions()).extracting(GeneratedQuestion::topic).containsExactly("주제 A", "주제 B", "주제 A");
        assertThat(server.last().path()).isEqualTo("/ai/v1/review-questions");
        JsonNode body = mapper.readTree(server.last().body());
        assertThat(body.get("count").asInt()).isEqualTo(3);
        assertThat(body.get("weakTopics")).hasSize(2);
        JsonNode wrong = body.get("wrongQuestions").get(0);
        assertThat(wrong.get("answerIndex").asInt()).isEqualTo(0);
        assertThat(wrong.has("selectedIndex")).isTrue();
        assertThat(wrong.get("selectedIndex").isNull()).isTrue();
    }

    @Test
    void AI3_topic이_weakTopics에_없으면_AI_SERVER_ERROR() {
        server.respondSuccess("{\"questions\":[" + FakeAiServer.multipleChoice(1, "NORMAL", "주제 A") + ","
                + FakeAiServer.multipleChoice(2, "NORMAL", "주제 a") + ","
                + FakeAiServer.multipleChoice(3, "NORMAL", "주제 A") + "]}");

        assertError(() -> client.generateReviewQuestions(reviewRequest(List.of("주제 A"))), ErrorCode.AI_SERVER_ERROR);
    }

    // AI4

    @Test
    void AI4_날짜를_한국_시간_ISO_문자열로_보낸다() {
        server.respondSuccess("{\"summary\":\"복습 총평\"}");
        ReviewFeedbackRequest request = new ReviewFeedbackRequest("수학", "처음 총평",
                OffsetDateTime.parse("2026-10-29T20:00:00+09:00"), OffsetDateTime.parse("2026-11-01T19:40:00+09:00"),
                true, 3, 3, List.of(new ReviewFeedbackRequest.TopicResult("주제 A", true)));

        ReviewFeedbackResult result = client.generateReviewFeedback(request);

        assertThat(result.summary()).isEqualTo("복습 총평");
        assertThat(server.last().path()).isEqualTo("/ai/v1/review-feedback");
        JsonNode body = mapper.readTree(server.last().body());
        assertThat(body.get("studiedAt").asString()).isEqualTo("2026-10-29T20:00:00+09:00");
        assertThat(body.get("reviewedAt").asString()).isEqualTo("2026-11-01T19:40:00+09:00");
        assertThat(body.get("passed").asBoolean()).isTrue();
        assertThat(body.get("results").get(0).get("isCorrect").asBoolean()).isTrue();
    }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(expected);
    }
}

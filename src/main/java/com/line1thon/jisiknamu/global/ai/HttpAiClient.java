package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * AI 서버 HTTP 호출 (ai.server.mock=false). 기본 경로 /ai/v1, X-Internal-Key 헤더, 타임아웃 50초.
 * AI 서버의 에러 코드는 AI 서버 API 명세 1장 표대로 메인 서버 에러 코드로 바꾼다.
 * 응답이 명세와 다르면 저장하지 않고 AI_SERVER_ERROR를 던진다.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "ai.server.mock", havingValue = "false")
public class HttpAiClient implements AiClient {

    private static final String BASE_PATH = "/ai/v1";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final int QUIZ_SIZE = 7;
    private static final int OPTION_COUNT = 4;
    private static final int MAX_TOPIC_LENGTH = 100;
    private static final int MAX_EXCLUDE_QUESTIONS = 50;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String internalKey;
    private final Duration timeout;

    @Autowired
    public HttpAiClient(ObjectMapper objectMapper,
                        @Value("${ai.server.url}") String serverUrl,
                        @Value("${ai.server.internal-key}") String internalKey,
                        @Value("${ai.server.timeout-seconds}") long timeoutSeconds) {
        this(objectMapper, serverUrl, internalKey, Duration.ofSeconds(timeoutSeconds));
    }

    HttpAiClient(ObjectMapper objectMapper, String serverUrl, String internalKey, Duration timeout) {
        if (internalKey == null || internalKey.isBlank()) {
            throw new IllegalStateException("AI_INTERNAL_KEY가 비어 있습니다. AI 서버를 쓰지 않으면 AI_MOCK=true로 둡니다.");
        }
        this.httpClient = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        this.objectMapper = objectMapper;
        this.baseUrl = serverUrl.replaceAll("/+$", "") + BASE_PATH;
        this.internalKey = internalKey;
        this.timeout = timeout;
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerateRequest request) {
        QuizGenerateRequest sent = new QuizGenerateRequest(request.quizSetType(), request.subjectName(),
                request.studyLevel(), request.inputType(), request.imageUrls(), request.content(), request.memo(),
                request.weakTopics(), recent(request.excludeQuestions()));
        GeneratedQuizResponse response = post("/questions", sent, GeneratedQuizResponse.class);
        if (Boolean.FALSE.equals(response.readable())) {
            throw new BusinessException(ErrorCode.NOTE_UNREADABLE);
        }
        // 재도전·복습은 사진을 다시 읽지 않고 이 글로 출제하므로, 사진 공부면 꼭 있어야 한다
        if (request.inputType() == InputType.IMAGE) {
            require(hasText(response.extractedText()), "AI1 사진 공부인데 extractedText가 비어 있음");
        }
        List<GeneratedQuestion> questions = response.questions();
        require(questions != null && questions.size() == QUIZ_SIZE, "AI1 문제 수가 7개가 아님");
        for (int i = 0; i < QUIZ_SIZE; i++) {
            GeneratedQuestion question = questions.get(i);
            require(question.number() == i + 1, "AI1 문제 번호가 1~7 순서가 아님");
            if (i == QUIZ_SIZE - 1) {
                checkEssay(question);
            } else {
                checkMultipleChoice(question);
            }
        }
        return new GeneratedQuiz(response.extractedText(), questions);
    }

    @Override
    public FeedbackResult generateFeedback(FeedbackRequest request) {
        FeedbackResult result = post("/feedback", request, FeedbackResult.class);
        require(hasText(result.summary()) && hasText(result.essayFeedback()), "AI2 summary 또는 essayFeedback이 비어 있음");
        return result;
    }

    @Override
    public ReviewQuestionsResult generateReviewQuestions(ReviewQuestionsRequest request) {
        ReviewQuestionsRequest sent = new ReviewQuestionsRequest(request.subjectName(), request.studyLevel(),
                request.content(), request.memo(), request.weakTopics(), request.wrongQuestions(),
                recent(request.excludeQuestions()), request.count());
        ReviewQuestionsResult result = post("/review-questions", sent, ReviewQuestionsResult.class);
        List<GeneratedQuestion> questions = result.questions();
        require(questions != null && questions.size() == request.count(), "AI3 문제 수가 요청과 다름");
        for (int i = 0; i < questions.size(); i++) {
            GeneratedQuestion question = questions.get(i);
            require(question.number() == i + 1, "AI3 문제 번호가 순서대로가 아님");
            checkMultipleChoice(question);
            // RECOVERED 처리는 topic 글자가 WEAK 주제와 같아야 한다
            require(request.weakTopics().contains(question.topic()), "AI3 topic이 weakTopics에 없음");
        }
        return result;
    }

    @Override
    public ReviewFeedbackResult generateReviewFeedback(ReviewFeedbackRequest request) {
        ReviewFeedbackResult result = post("/review-feedback", request, ReviewFeedbackResult.class);
        require(hasText(result.summary()), "AI4 summary가 비어 있음");
        return result;
    }

    private <T> T post(String path, Object body, Class<T> responseType) {
        HttpResponse<String> response = send(path, body);
        JsonNode root = readTree(path, response);
        JsonNode data = root.path("data");
        if (response.statusCode() == 200 && root.path("success").asBoolean(false) && data.isObject()) {
            try {
                return objectMapper.readerFor(responseType)
                        .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .readValue(data);
            } catch (JacksonException e) {
                log.warn("AI 서버 {} 응답 형식이 명세와 다름: {}", path, e.getOriginalMessage());
                throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
            }
        }
        String code = root.path("error").path("code").asString("");
        log.warn("AI 서버 {} 실패: HTTP {}, code={}, message={}", path, response.statusCode(), code,
                root.path("error").path("message").asString(""));
        throw new BusinessException(toErrorCode(code));
    }

    private HttpResponse<String> send(String path, Object body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("X-Internal-Key", internalKey)
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body), StandardCharsets.UTF_8))
                .build();
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (HttpTimeoutException e) {
            log.warn("AI 서버 {} 시간 초과 ({}초)", path, timeout.toSeconds());
            throw new BusinessException(ErrorCode.AI_TIMEOUT);
        } catch (IOException e) {
            log.warn("AI 서버 {} 연결 실패: {}", path, e.toString());
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    private JsonNode readTree(String path, HttpResponse<String> response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root != null && root.isObject()) {
                return root;
            }
        } catch (JacksonException e) {
            // 아래에서 처리한다
        }
        // nginx 에러 페이지처럼 JSON이 아닌 응답
        log.warn("AI 서버 {} 응답이 JSON이 아님: HTTP {}", path, response.statusCode());
        throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
    }

    /**
     * AI 서버 API 명세 1장 에러 코드 표. 요청 형식·인증 문제는 메인 서버 잘못이라 INTERNAL_ERROR다.
     */
    static ErrorCode toErrorCode(String aiCode) {
        return switch (aiCode) {
            case "NOTE_UNREADABLE" -> ErrorCode.NOTE_UNREADABLE;
            case "CONTENT_TOO_SHORT" -> ErrorCode.CONTENT_TOO_SHORT;
            case "AI_TIMEOUT" -> ErrorCode.AI_TIMEOUT;
            case "INVALID_REQUEST", "UNAUTHORIZED" -> ErrorCode.INTERNAL_ERROR;
            default -> ErrorCode.AI_SERVER_ERROR; // IMAGE_FETCH_FAILED, AI_GENERATION_FAILED, 그 밖
        };
    }

    private void checkMultipleChoice(GeneratedQuestion question) {
        checkTopic(question);
        require(question.questionType() == QuestionType.MULTIPLE_CHOICE && question.difficulty() != Difficulty.ESSAY,
                "객관식 자리에 서술형이 옴");
        require(question.options() != null && question.options().size() == OPTION_COUNT, "객관식 보기가 4개가 아님");
        require(question.answerIndex() != null && question.answerIndex() >= 0 && question.answerIndex() < OPTION_COUNT,
                "answerIndex가 0~3이 아님");
        require(hasText(question.content()), "문제 본문이 비어 있음");
    }

    private void checkEssay(GeneratedQuestion question) {
        checkTopic(question);
        require(question.questionType() == QuestionType.ESSAY && question.difficulty() == Difficulty.ESSAY,
                "7번이 서술형이 아님");
        require(hasText(question.content()) && hasText(question.modelAnswer()), "서술형 본문 또는 모범답안이 비어 있음");
        require(question.gradingPoints() != null && !question.gradingPoints().isEmpty(), "서술형 채점 포인트가 없음");
    }

    private void checkTopic(GeneratedQuestion question) {
        require(hasText(question.topic()) && question.topic().length() <= MAX_TOPIC_LENGTH, "topic이 1~100자가 아님");
    }

    /**
     * AI 서버는 excludeQuestions를 50개까지만 받는다. 목록은 오래된 문제부터라 최근 50개를 보낸다.
     */
    static List<String> recent(List<String> excludeQuestions) {
        if (excludeQuestions == null || excludeQuestions.size() <= MAX_EXCLUDE_QUESTIONS) {
            return excludeQuestions;
        }
        return List.copyOf(excludeQuestions.subList(excludeQuestions.size() - MAX_EXCLUDE_QUESTIONS,
                excludeQuestions.size()));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static void require(boolean condition, String reason) {
        if (!condition) {
            log.warn("AI 서버 응답 검증 실패: {}", reason);
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
    }

    /** AI1 응답 원형. readable=false면 NOTE_UNREADABLE로 본다. */
    record GeneratedQuizResponse(Boolean readable, String extractedText, List<GeneratedQuestion> questions) {
    }
}

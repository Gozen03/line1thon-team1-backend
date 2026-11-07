package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * AI 서버가 없을 때 쓰는 가짜 구현. 프론트가 에러 화면을 확인할 수 있도록 메모에 아래 값을 넣으면 그 결과를 낸다.
 * [mock:unreadable] NOTE_UNREADABLE, [mock:short] CONTENT_TOO_SHORT, [mock:error] AI_SERVER_ERROR, [mock:timeout] AI_TIMEOUT
 */
@Component
@ConditionalOnProperty(name = "ai.server.mock", havingValue = "true")
public class MockAiClient implements AiClient {

    private static final List<Difficulty> MULTIPLE_CHOICE_DIFFICULTIES = List.of(
            Difficulty.EASY, Difficulty.EASY, Difficulty.NORMAL, Difficulty.NORMAL, Difficulty.HARD, Difficulty.HARD);

    private final long delayMillis;

    public MockAiClient(@Value("${ai.server.mock-delay-ms}") long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerateRequest request) {
        waitLikeAiServer();
        String memo = request.memo() == null ? "" : request.memo();
        if (memo.contains("[mock:unreadable]")) {
            return GeneratedQuiz.unreadable();
        }
        if (memo.contains("[mock:short]")) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_SHORT);
        }
        if (memo.contains("[mock:error]")) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
        if (memo.contains("[mock:timeout]")) {
            throw new BusinessException(ErrorCode.AI_TIMEOUT);
        }

        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int number = 1; number <= MULTIPLE_CHOICE_DIFFICULTIES.size(); number++) {
            questions.add(multipleChoice(number, request));
        }
        questions.add(essay(request));
        String extractedText = request.inputType() == InputType.IMAGE
                ? "(Mock) 사진 %d장에서 읽은 공부 내용".formatted(request.imageUrls().size())
                : null;
        return new GeneratedQuiz(true, extractedText, questions);
    }

    private GeneratedQuestion multipleChoice(int number, QuizGenerateRequest request) {
        String topic = topic(number, request);
        return new GeneratedQuestion(number, MULTIPLE_CHOICE_DIFFICULTIES.get(number - 1), QuestionType.MULTIPLE_CHOICE,
                topic, "(Mock) %s %d번 문제: %s에 대한 설명으로 옳은 것은?".formatted(request.subjectName(), number, topic),
                List.of("보기 %d-1".formatted(number), "보기 %d-2".formatted(number),
                        "보기 %d-3".formatted(number), "보기 %d-4".formatted(number)),
                (number - 1) % 4, "(Mock) %d번 해설".formatted(number), null, null);
    }

    private GeneratedQuestion essay(QuizGenerateRequest request) {
        String topic = topic(7, request);
        return new GeneratedQuestion(7, Difficulty.ESSAY, QuestionType.ESSAY, topic,
                "(Mock) %s: %s의 의미를 설명해 보세요.".formatted(request.subjectName(), topic),
                null, null, "(Mock) 서술형 해설", "(Mock) 모범답안",
                List.of("(Mock) 채점 포인트 1", "(Mock) 채점 포인트 2"));
    }

    private String topic(int number, QuizGenerateRequest request) {
        List<String> weakTopics = request.weakTopics();
        if (weakTopics != null && !weakTopics.isEmpty()) {
            return weakTopics.get((number - 1) % weakTopics.size());
        }
        return "%s 주제 %d".formatted(request.subjectName(), number);
    }

    private void waitLikeAiServer() {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
    }
}

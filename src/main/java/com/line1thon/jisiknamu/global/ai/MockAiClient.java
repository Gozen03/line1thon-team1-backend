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
 * AI 서버가 없을 때 쓰는 가짜 구현. 프론트가 에러 화면을 확인할 수 있도록 아래 값을 넣으면 그 결과를 낸다.
 * S1 메모: [mock:unreadable] NOTE_UNREADABLE, [mock:short] CONTENT_TOO_SHORT, [mock:error] AI_SERVER_ERROR, [mock:timeout] AI_TIMEOUT
 * S2 서술형 답: [mock:error], [mock:timeout]
 */
@Component
@ConditionalOnProperty(name = "ai.server.mock", havingValue = "true")
public class MockAiClient implements AiClient {

    private static final List<Difficulty> MULTIPLE_CHOICE_DIFFICULTIES = List.of(
            Difficulty.EASY, Difficulty.EASY, Difficulty.NORMAL, Difficulty.NORMAL, Difficulty.HARD, Difficulty.HARD);
    private static final String EMPTY_ESSAY_FEEDBACK = "답을 적지 않았어요. 모범답안을 보고 핵심을 정리해 보세요.";

    private final long delayMillis;

    public MockAiClient(@Value("${ai.server.mock-delay-ms}") long delayMillis) {
        this.delayMillis = delayMillis;
    }

    @Override
    public GeneratedQuiz generateQuiz(QuizGenerateRequest request) {
        waitLikeAiServer();
        String memo = request.memo() == null ? "" : request.memo();
        if (memo.contains("[mock:unreadable]")) {
            throw new BusinessException(ErrorCode.NOTE_UNREADABLE);
        }
        if (memo.contains("[mock:short]")) {
            throw new BusinessException(ErrorCode.CONTENT_TOO_SHORT);
        }
        throwIfRequested(memo);

        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int number = 1; number <= MULTIPLE_CHOICE_DIFFICULTIES.size(); number++) {
            questions.add(multipleChoice(number, MULTIPLE_CHOICE_DIFFICULTIES.get(number - 1), request.subjectName(),
                    topic(number, request.subjectName(), request.weakTopics()), request.excludeQuestions()));
        }
        questions.add(essay(request));
        String extractedText = request.inputType() == InputType.IMAGE
                ? "(Mock) 사진 %d장에서 읽은 공부 내용".formatted(request.imageUrls().size())
                : null;
        return new GeneratedQuiz(extractedText, questions);
    }

    @Override
    public FeedbackResult generateFeedback(FeedbackRequest request) {
        waitLikeAiServer();
        String essayText = request.essay().essayText();
        throwIfRequested(essayText == null ? "" : essayText);
        String essayFeedback = essayText == null || essayText.isBlank()
                ? EMPTY_ESSAY_FEEDBACK
                : "(Mock) 핵심을 잘 설명했어요. 채점 포인트를 하나 더 넣으면 더 좋아요.";
        return new FeedbackResult("(Mock) %d문제 중 %d문제를 맞혔어요. 등급은 %s예요."
                .formatted(request.gradedCount(), request.correctCount(), request.grade()), essayFeedback);
    }

    @Override
    public ReviewQuestionsResult generateReviewQuestions(ReviewQuestionsRequest request) {
        waitLikeAiServer();
        List<GeneratedQuestion> questions = new ArrayList<>();
        for (int number = 1; number <= request.count(); number++) {
            questions.add(multipleChoice(number, Difficulty.NORMAL, request.subjectName(),
                    request.weakTopics().get((number - 1) % request.weakTopics().size()), request.excludeQuestions()));
        }
        return new ReviewQuestionsResult(questions);
    }

    @Override
    public ReviewFeedbackResult generateReviewFeedback(ReviewFeedbackRequest request) {
        waitLikeAiServer();
        return new ReviewFeedbackResult("(Mock) 복습에서 %d문제를 모두 맞혀 잘 이해했어요.".formatted(request.gradedCount()));
    }

    private GeneratedQuestion multipleChoice(int number, Difficulty difficulty, String subjectName, String topic,
                                             List<String> excludeQuestions) {
        return new GeneratedQuestion(number, difficulty, QuestionType.MULTIPLE_CHOICE, topic,
                "(Mock) %s %d번 문제%s: %s에 대한 설명으로 옳은 것은?"
                        .formatted(subjectName, number, differentFromBefore(excludeQuestions), topic),
                List.of("보기 %d-1".formatted(number), "보기 %d-2".formatted(number),
                        "보기 %d-3".formatted(number), "보기 %d-4".formatted(number)),
                (number - 1) % 4, "(Mock) %d번 해설".formatted(number), null, null);
    }

    private GeneratedQuestion essay(QuizGenerateRequest request) {
        String topic = topic(7, request.subjectName(), request.weakTopics());
        return new GeneratedQuestion(7, Difficulty.ESSAY, QuestionType.ESSAY, topic,
                "(Mock) %s%s: %s의 의미를 설명해 보세요."
                        .formatted(request.subjectName(), differentFromBefore(request.excludeQuestions()), topic),
                null, null, null, "(Mock) 모범답안",
                List.of("(Mock) 채점 포인트 1", "(Mock) 채점 포인트 2"));
    }

    private String topic(int number, String subjectName, List<String> weakTopics) {
        if (weakTopics != null && !weakTopics.isEmpty()) {
            return weakTopics.get((number - 1) % weakTopics.size());
        }
        return "%s 주제 %d".formatted(subjectName, number);
    }

    // 실제 AI처럼 이전 세트와 다른 문제를 내도록 본문을 바꾼다
    private String differentFromBefore(List<String> excludeQuestions) {
        return excludeQuestions == null || excludeQuestions.isEmpty() ? "" : " (새 문제 %d)".formatted(excludeQuestions.size());
    }

    private void throwIfRequested(String text) {
        if (text.contains("[mock:error]")) {
            throw new BusinessException(ErrorCode.AI_SERVER_ERROR);
        }
        if (text.contains("[mock:timeout]")) {
            throw new BusinessException(ErrorCode.AI_TIMEOUT);
        }
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

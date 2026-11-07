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
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

// Mock 응답이 AI 서버 API 명세(AI1~AI4)의 형식과 같은지 확인한다
class MockAiClientTest {

    private final MockAiClient client = new MockAiClient(0);

    private GeneratedQuiz quiz(InputType inputType, String memo, List<String> weakTopics, List<String> exclude) {
        return client.generateQuiz(new QuizGenerateRequest(QuizSetType.INITIAL, "수학", StudyLevel.HIGH, inputType,
                inputType == InputType.IMAGE ? List.of("https://example.invalid/a") : List.of(),
                inputType == InputType.TEXT ? "공부 내용" : null, memo, weakTopics, exclude));
    }

    @Test
    void AI1_객관식과_서술형의_필드가_명세대로_채워진다() {
        GeneratedQuiz quiz = quiz(InputType.TEXT, null, List.of(), List.of());

        assertThat(quiz.extractedText()).isNull();
        assertThat(quiz.questions()).hasSize(7);
        assertThat(quiz.questions()).extracting(GeneratedQuestion::number).containsExactly(1, 2, 3, 4, 5, 6, 7);
        assertThat(quiz.questions()).extracting(GeneratedQuestion::difficulty).containsExactly(
                Difficulty.EASY, Difficulty.EASY, Difficulty.NORMAL, Difficulty.NORMAL,
                Difficulty.HARD, Difficulty.HARD, Difficulty.ESSAY);

        GeneratedQuestion multipleChoice = quiz.questions().get(0);
        assertThat(multipleChoice.questionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(multipleChoice.options()).hasSize(4);
        assertThat(multipleChoice.answerIndex()).isBetween(0, 3);
        assertThat(multipleChoice.explanation()).isNotBlank();
        assertThat(multipleChoice.modelAnswer()).isNull();
        assertThat(multipleChoice.gradingPoints()).isNull();

        GeneratedQuestion essay = quiz.questions().get(6);
        assertThat(essay.questionType()).isEqualTo(QuestionType.ESSAY);
        assertThat(essay.options()).isNull();
        assertThat(essay.answerIndex()).isNull();
        assertThat(essay.explanation()).isNull();
        assertThat(essay.modelAnswer()).isNotBlank();
        assertThat(essay.gradingPoints()).hasSizeBetween(2, 3);
    }

    @Test
    void AI1_사진이면_읽은_내용을_주고_못_읽으면_에러다() {
        assertThat(quiz(InputType.IMAGE, null, List.of(), List.of()).extractedText()).isNotBlank();
        assertThatThrownBy(() -> quiz(InputType.IMAGE, "[mock:unreadable]", List.of(), List.of()))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.NOTE_UNREADABLE));
    }

    @Test
    void AI1_재도전은_약한_주제를_반영하고_이전_문제와_달라진다() {
        GeneratedQuiz first = quiz(InputType.TEXT, null, List.of(), List.of());
        List<String> before = first.questions().stream().map(GeneratedQuestion::content).toList();

        GeneratedQuiz retry = quiz(InputType.TEXT, null, List.of("판별식"), before);

        assertThat(retry.questions()).extracting(GeneratedQuestion::topic).containsOnly("판별식");
        assertThat(retry.questions()).extracting(GeneratedQuestion::content).doesNotContainAnyElementsOf(before);
    }

    @Test
    void AI2_서술형_답이_비면_고정_문구를_준다() {
        FeedbackResult empty = client.generateFeedback(feedbackRequest(null));
        assertThat(empty.essayFeedback()).isEqualTo("답을 적지 않았어요. 모범답안을 보고 핵심을 정리해 보세요.");
        assertThat(empty.summary()).isNotBlank();

        assertThat(client.generateFeedback(feedbackRequest("꼭짓점은 ...")).essayFeedback())
                .isNotEqualTo(empty.essayFeedback());
    }

    @Test
    void AI3_복습_문제는_객관식이고_주제가_약한_주제와_같다() {
        List<String> weakTopics = List.of("판별식", "평행이동");
        ReviewQuestionsResult result = client.generateReviewQuestions(new ReviewQuestionsRequest(
                "수학", StudyLevel.HIGH, "공부 내용", "메모", weakTopics,
                List.of(new ReviewQuestionsRequest.WrongQuestion("판별식", "문제", List.of("a", "b", "c", "d"), 0, 2)),
                List.of(), 3));

        assertThat(result.questions()).hasSize(3);
        assertThat(result.questions()).extracting(GeneratedQuestion::questionType).containsOnly(QuestionType.MULTIPLE_CHOICE);
        assertThat(result.questions()).extracting(GeneratedQuestion::topic).isSubsetOf(weakTopics);
    }

    @Test
    void AI4_복습_총평을_준다() {
        ReviewFeedbackResult result = client.generateReviewFeedback(new ReviewFeedbackRequest(
                "수학", "원래 총평", OffsetDateTime.now(), OffsetDateTime.now(), true, 3, 3,
                List.of(new ReviewFeedbackRequest.TopicResult("판별식", true))));

        assertThat(result.summary()).isNotBlank();
    }

    private FeedbackRequest feedbackRequest(String essayText) {
        return new FeedbackRequest("수학", StudyLevel.HIGH, 3, 6, LeafGrade.AUTUMN,
                List.of(new FeedbackRequest.QuestionResult(1, "주제", Difficulty.EASY, true)),
                new FeedbackRequest.Essay("문제", "모범답안", List.of("포인트"), essayText));
    }
}

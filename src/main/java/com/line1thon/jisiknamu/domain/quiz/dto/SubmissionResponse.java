package com.line1thon.jisiknamu.domain.quiz.dto;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * S2 응답 (첫 풀이·재도전).
 */
public record SubmissionResponse(
        @Schema(description = "문제 세트 ID", example = "301") Long quizSetId,
        @Schema(description = "세트 종류", example = "INITIAL") QuizSetType quizSetType,
        @Schema(description = "맞힌 개수 (서술형 제외)", example = "3") int correctCount,
        @Schema(description = "채점 대상 수", example = "6") int gradedCount,
        @Schema(description = "생기거나 바뀐 잎") LeafResult leaf,
        @Schema(description = "피드백") Feedback feedback,
        @Schema(description = "문제별 결과. number 순서") List<ResultItem> results) {

    public record LeafResult(
            @Schema(description = "잎 ID", example = "13") Long leafId,
            @Schema(description = "나무 ID", example = "1") Long treeId,
            @Schema(description = "자리 1~24", example = "13") int position,
            @Schema(description = "등급", example = "AUTUMN") LeafGrade grade,
            @Schema(description = "등급 안내 문구", example = "조금 더 다지면 초록잎이 돼요") String message,
            @Schema(description = "복습 대상인지 (단풍잎·갈색잎)", example = "true") boolean reviewTarget,
            @Schema(description = "복습 가능 시각. 복습 대상이 아니면 null", example = "2026-11-01T20:00:00+09:00") OffsetDateTime reviewAvailableAt,
            @Schema(description = "이 나무의 전체 잎 수", example = "13") long treeLeafCount) {
    }

    public record Feedback(
            @Schema(description = "AI 총평", example = "기본은 잘 잡혔어요. 조금만 더 다지면 초록잎이 될 수 있어요.") String summary,
            @Schema(description = "맞힌 문제의 주제 (중복 제거)") List<String> strengths,
            @Schema(description = "틀린 문제의 주제 (중복 제거). 꽃봉오리 화면의 \"먼저 다시 볼 부분\"에 쓴다") List<String> weaknesses,
            @Schema(description = "서술형 AI 피드백", example = "꼭짓점이 최솟값이 되는 점이라는 걸 잘 설명했어요.") String essayFeedback) {
    }

    public record ResultItem(
            @Schema(description = "문제 ID", example = "202") Long questionId,
            @Schema(description = "문제 번호", example = "4") int number,
            @Schema(description = "문제 주제", example = "판별식과 x절편 개수") String topic,
            @Schema(description = "난이도", example = "NORMAL") Difficulty difficulty,
            @Schema(description = "문제 형식", example = "MULTIPLE_CHOICE") QuestionType questionType,
            @Schema(description = "내가 고른 번호. 서술형이거나 안 풀었으면 null", example = "2") Integer selectedIndex,
            @Schema(description = "정답 번호. 서술형은 null", example = "0") Integer answerIndex,
            @Schema(description = "정답 여부. 서술형은 null", example = "false") Boolean isCorrect,
            @Schema(description = "해설. 서술형은 null", example = "판별식 D = −16 < 0 이므로 x절편이 없다.") String explanation,
            @Schema(description = "서술형 답. 객관식은 null") String essayText,
            @Schema(description = "서술형 모범답안. 객관식은 null") String modelAnswer) {
    }
}

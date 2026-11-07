package com.line1thon.jisiknamu.domain.quiz.dto;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * S2 응답 (복습).
 */
public record ReviewSubmissionResponse(
        @Schema(description = "문제 세트 ID", example = "355") Long quizSetId,
        @Schema(description = "세트 종류", example = "REVIEW") QuizSetType quizSetType,
        @Schema(description = "맞힌 개수", example = "3") int correctCount,
        @Schema(description = "채점 대상 수", example = "3") int gradedCount,
        @Schema(description = "3문제를 모두 맞혔는지", example = "true") boolean passed,
        @Schema(description = "잎") LeafResult leaf,
        @Schema(description = "복습으로 이해한 주제 (그 잎의 약한 주제 전부). 실패하면 빈 배열") List<String> recoveredTopics,
        @Schema(description = "성공하면 복습 결과까지 반영한 총평, 실패하면 응원 문구", example = "처음엔 판별식 부분이 부족했지만, 11월 1일 복습에서 3문제를 모두 맞혀 잘 이해했어요.") String summary,
        @Schema(description = "이 나무에 남은 복습할 잎 수", example = "2") long remainingReviewCount,
        @Schema(description = "문제별 결과. number 순서") List<SubmissionResponse.ResultItem> results) implements SubmissionResult {

    public record LeafResult(
            @Schema(description = "잎 ID", example = "13") Long leafId,
            @Schema(description = "복습 전 등급", example = "AUTUMN") LeafGrade beforeGrade,
            @Schema(description = "지금 등급. 성공하면 GREEN", example = "GREEN") LeafGrade grade,
            @Schema(description = "공부한 날", example = "2026-10-29T20:00:00+09:00") OffsetDateTime studiedAt,
            @Schema(description = "다음 복습 가능 시각. 성공하면 null") OffsetDateTime reviewAvailableAt) {
    }
}

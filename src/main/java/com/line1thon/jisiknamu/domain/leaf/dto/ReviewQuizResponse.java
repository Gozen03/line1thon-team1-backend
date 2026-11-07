package com.line1thon.jisiknamu.domain.leaf.dto;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * R2 응답. 정답·해설은 넣지 않는다.
 */
public record ReviewQuizResponse(
        @Schema(description = "문제 세트 ID. 답 제출(S2)에 쓴다", example = "355") Long quizSetId,
        @Schema(description = "세트 종류. 항상 REVIEW", example = "REVIEW") QuizSetType quizSetType,
        @Schema(description = "잎 ID", example = "13") Long leafId,
        @Schema(description = "객관식 3문제") List<QuizSetResponse.QuestionItem> questions) {
}

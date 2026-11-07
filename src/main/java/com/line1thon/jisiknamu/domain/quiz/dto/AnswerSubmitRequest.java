package com.line1thon.jisiknamu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record AnswerSubmitRequest(
        @Schema(description = "답 목록. 문제 수만큼 보낸다 (첫 풀이·재도전 7개, 복습 3개)", requiredMode = Schema.RequiredMode.REQUIRED)
        List<AnswerItem> answers) {

    public record AnswerItem(
            @Schema(description = "문제 ID", example = "201", requiredMode = Schema.RequiredMode.REQUIRED)
            Long questionId,
            @Schema(description = "객관식 선택 번호 0~3. 안 푼 문제는 null이고 오답이다", example = "0")
            Integer selectedIndex,
            @Schema(description = "서술형 답. 1,000자 이하", example = "그래프가 가장 낮아지는 점이고...")
            String essayText) {
    }
}

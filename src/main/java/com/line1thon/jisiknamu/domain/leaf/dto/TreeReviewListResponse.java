package com.line1thon.jisiknamu.domain.leaf.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * R1 응답.
 */
public record TreeReviewListResponse(
        @Schema(description = "나무 ID", example = "1") Long treeId,
        @Schema(description = "나무 과목 이름", example = "수학") String subjectName,
        @Schema(description = "복습할 잎 목록. 오래된 공부부터. 비어 있으면 복습 알림 대신 나무 화면을 보여 준다") List<ReviewItem> reviews) {
}

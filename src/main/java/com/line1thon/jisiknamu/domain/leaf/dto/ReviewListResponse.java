package com.line1thon.jisiknamu.domain.leaf.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * R0 응답.
 */
public record ReviewListResponse(
        @Schema(description = "지금 복습할 수 있는 잎 수. 하단 탭 배지에 쓸 수 있다", example = "5") int totalCount,
        @Schema(description = "아직 복습 시점이 안 된 잎 중 가장 이른 복습 가능 시각. 없으면 null", example = "2026-11-05T20:00:00+09:00") OffsetDateTime nextReviewAt,
        @Schema(description = "복습할 잎 목록. 오래된 공부부터") List<ReviewItem> reviews) {
}

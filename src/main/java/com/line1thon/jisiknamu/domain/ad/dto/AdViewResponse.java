package com.line1thon.jisiknamu.domain.ad.dto;

import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdViewResponse(
        @Schema(description = "광고 위치", example = "LEAF_PLACEMENT") AdPlacement adPlacement,
        @Schema(description = "이 위치의 누적 횟수 (이번 호출 포함)", example = "3") int count,
        @Schema(description = "이번에 광고를 띄울지. count가 1이거나 3의 배수면 true", example = "true") boolean showAd) {
}

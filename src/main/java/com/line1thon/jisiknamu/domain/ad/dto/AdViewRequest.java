package com.line1thon.jisiknamu.domain.ad.dto;

import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import io.swagger.v3.oas.annotations.media.Schema;

public record AdViewRequest(
        @Schema(description = "광고 위치: 잎 배치 후 / 기록 열람", example = "LEAF_PLACEMENT", requiredMode = Schema.RequiredMode.REQUIRED)
        AdPlacement adPlacement) {
}

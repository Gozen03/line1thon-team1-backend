package com.line1thon.jisiknamu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameUpdateRequest(
        @Schema(description = "새 닉네임. 앞뒤 공백을 지운 뒤 2~10자", example = "재민2", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname) {
}

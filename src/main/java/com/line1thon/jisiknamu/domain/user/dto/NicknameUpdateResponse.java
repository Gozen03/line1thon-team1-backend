package com.line1thon.jisiknamu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record NicknameUpdateResponse(
        @Schema(description = "회원 ID", example = "1") Long userId,
        @Schema(description = "바뀐 닉네임", example = "재민2") String nickname) {
}

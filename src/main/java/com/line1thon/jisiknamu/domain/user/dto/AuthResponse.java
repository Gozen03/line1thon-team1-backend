package com.line1thon.jisiknamu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(description = "회원 ID", example = "1") Long userId,
        @Schema(description = "닉네임", example = "재민") String nickname,
        @Schema(description = "액세스 토큰. 이후 요청의 Authorization 헤더에 넣는다", example = "eyJhbGciOi...") String accessToken) {
}

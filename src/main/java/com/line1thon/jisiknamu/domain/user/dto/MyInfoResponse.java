package com.line1thon.jisiknamu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record MyInfoResponse(
        @Schema(description = "회원 ID", example = "1") Long userId,
        @Schema(description = "닉네임", example = "재민") String nickname,
        @Schema(description = "가입일", example = "2026-11-03T11:00:00+09:00") OffsetDateTime createdAt) {
}

package com.line1thon.jisiknamu.domain.user.dto;

import java.time.OffsetDateTime;

public record MyInfoResponse(Long userId, String nickname, OffsetDateTime createdAt) {
}

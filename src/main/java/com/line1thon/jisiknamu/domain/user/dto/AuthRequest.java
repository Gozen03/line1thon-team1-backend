package com.line1thon.jisiknamu.domain.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 가입(U1)·로그인(U2) 요청. 값 검사는 명세의 에러 코드로 응답하도록 UserService에서 한다.
 */
public record AuthRequest(
        @Schema(description = "닉네임. 앞뒤 공백을 지운 뒤 2~10자", example = "재민", requiredMode = Schema.RequiredMode.REQUIRED)
        String nickname,
        @Schema(description = "숫자 비밀번호 4~6자리 (문자열)", example = "1234", requiredMode = Schema.RequiredMode.REQUIRED)
        String password) {
}

package com.line1thon.jisiknamu.domain.user.controller;

import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.dto.AuthResponse;
import com.line1thon.jisiknamu.domain.user.service.UserService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "U2. 로그인 + 토큰 발급", description = """
            토큰이 필요 없습니다.

            닉네임이 없든 비밀번호가 틀리든, 값이 빠졌든 모두 `LOGIN_FAILED` 하나로 응답합니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.LOGIN_FAILED})
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody AuthRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @Operation(summary = "U5. 로그아웃", description = """
            응답 본문이 없습니다 (204). 서버는 하는 일이 없고, **프론트가 저장한 토큰을 지우면** 로그아웃이 끝납니다.
            """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PostMapping("/logout")
    public void logout() {
    }
}

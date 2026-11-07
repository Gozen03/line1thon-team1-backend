package com.line1thon.jisiknamu.domain.user.controller;

import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.dto.AuthResponse;
import com.line1thon.jisiknamu.domain.user.service.UserService;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    @Operation(summary = "U2. 로그인 + 토큰 발급")
    @SecurityRequirements
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@RequestBody AuthRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @Operation(summary = "U5. 로그아웃 (서버는 하는 일이 없고, 프론트가 토큰을 지운다)")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }
}

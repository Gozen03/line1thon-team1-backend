package com.line1thon.jisiknamu.domain.user.controller;

import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.dto.AuthResponse;
import com.line1thon.jisiknamu.domain.user.dto.MyInfoResponse;
import com.line1thon.jisiknamu.domain.user.dto.NicknameUpdateRequest;
import com.line1thon.jisiknamu.domain.user.dto.NicknameUpdateResponse;
import com.line1thon.jisiknamu.domain.user.service.UserService;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "U1. 가입 + 토큰 발급")
    @SecurityRequirements
    @PostMapping
    public ResponseEntity<ApiResponse<AuthResponse>> signUp(@RequestBody AuthRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(userService.signUp(request)));
    }

    @Operation(summary = "U3. 내 정보 조회")
    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userService.getMyInfo(userId));
    }

    @Operation(summary = "U4. 닉네임 수정")
    @PatchMapping("/me")
    public ApiResponse<NicknameUpdateResponse> updateNickname(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(userService.updateNickname(userId, request.nickname()));
    }

    @Operation(summary = "U6. 회원 탈퇴 (나무·기록 전부 삭제)")
    @DeleteMapping("/me")
    public ResponseEntity<Void> withdraw(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
        return ResponseEntity.noContent().build();
    }
}

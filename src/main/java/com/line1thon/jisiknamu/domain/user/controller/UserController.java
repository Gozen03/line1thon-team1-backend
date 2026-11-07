package com.line1thon.jisiknamu.domain.user.controller;

import com.line1thon.jisiknamu.domain.user.dto.AuthRequest;
import com.line1thon.jisiknamu.domain.user.dto.AuthResponse;
import com.line1thon.jisiknamu.domain.user.dto.MyInfoResponse;
import com.line1thon.jisiknamu.domain.user.dto.NicknameUpdateRequest;
import com.line1thon.jisiknamu.domain.user.dto.NicknameUpdateResponse;
import com.line1thon.jisiknamu.domain.user.service.UserService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "회원")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "U1. 가입 + 토큰 발급", description = """
            토큰이 필요 없습니다. 가입과 동시에 토큰을 주므로 바로 로그인 상태가 됩니다.

            - 닉네임은 앞뒤 공백을 지운 뒤 2~10자이고, 로그인 아이디 역할을 해서 중복될 수 없습니다.
            - 닉네임 중복은 대소문자를 구분하지 않습니다 (`Abc`와 `abc`는 같은 닉네임).
            - 비밀번호는 숫자 4~6자리이며 **문자열**로 보냅니다. 찾기 기능은 없습니다.
            - 닉네임이나 비밀번호가 빠져도 `NICKNAME_INVALID`·`PASSWORD_INVALID`입니다.
            """)
    @ApiErrors({ErrorCode.NICKNAME_INVALID, ErrorCode.PASSWORD_INVALID, ErrorCode.INVALID_REQUEST, ErrorCode.NICKNAME_DUPLICATED})
    @SecurityRequirements
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<AuthResponse> signUp(@RequestBody AuthRequest request) {
        return ApiResponse.success(userService.signUp(request));
    }

    @Operation(summary = "U3. 내 정보 조회", description = "내 정보 화면에 씁니다.")
    @GetMapping("/me")
    public ApiResponse<MyInfoResponse> getMyInfo(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userService.getMyInfo(userId));
    }

    @Operation(summary = "U4. 닉네임 수정", description = """
            - 바뀐 닉네임이 다음 로그인 때 쓰는 이름이 됩니다. 화면에 이 점을 한 줄로 안내해 주세요.
            - 토큰은 `userId` 기준이라 다시 발급하지 않습니다. 쓰던 토큰을 그대로 씁니다.
            - 지금 내 닉네임으로 수정해도 에러가 아닙니다.
            """)
    @ApiErrors({ErrorCode.NICKNAME_INVALID, ErrorCode.INVALID_REQUEST, ErrorCode.NICKNAME_DUPLICATED})
    @PatchMapping("/me")
    public ApiResponse<NicknameUpdateResponse> updateNickname(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody NicknameUpdateRequest request) {
        return ApiResponse.success(userService.updateNickname(userId, request.nickname()));
    }

    @Operation(summary = "U6. 회원 탈퇴", description = """
            응답 본문이 없습니다 (204). 나무·공부 기록·문제 세트·잎·광고 횟수·사진을 모두 지우며 되돌릴 수 없습니다.

            - 지운 닉네임은 다른 사람이 다시 쓸 수 있습니다.
            - 탈퇴 후 그 토큰은 401이 됩니다. 프론트는 토큰을 지우고 시작하기 화면으로 보냅니다.
            """)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/me")
    public void withdraw(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        userService.withdraw(userId);
    }
}

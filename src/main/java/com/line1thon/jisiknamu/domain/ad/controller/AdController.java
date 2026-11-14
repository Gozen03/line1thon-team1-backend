package com.line1thon.jisiknamu.domain.ad.controller;

import com.line1thon.jisiknamu.domain.ad.dto.AdViewRequest;
import com.line1thon.jisiknamu.domain.ad.dto.AdViewResponse;
import com.line1thon.jisiknamu.domain.ad.service.AdService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "광고")
@RestController
@RequiredArgsConstructor
public class AdController {

    private final AdService adService;

    @Operation(summary = "A1. 광고 표시 확인", description = """
            광고를 보여 줄 수 있는 순간마다 부릅니다. 서버가 횟수를 1 올리고 이번에 광고를 띄울지 알려 줍니다.

            - **언제:** 잎 배치는 팝업에서 닫기·다음을 누를 때 (`LEAF_PLACEMENT`), 기록 열람은 잎 상세를 열 때 (`RECORD_VIEW`)
            - **규칙:** 잎 배치와 기록 열람을 합친 회원의 전체 횟수(`count`)가 1·4·7·10…번째(3으로 나눈 나머지가 1)일 때 `showAd`가 `true`입니다. 광고는 3번에 1번 나오고, 두 위치에서 연달아 나오지 않습니다.
            - 횟수는 회원별로 셉니다 (11/14부터 위치를 합쳐서 셈).
            - 광고 제거 결제는 추후 개발입니다. "광고 없이 키우기" 버튼은 안내만 띄웁니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST})
    @PostMapping("/api/v1/ads/views")
    public ApiResponse<AdViewResponse> recordView(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody AdViewRequest request) {
        return ApiResponse.success(adService.recordView(userId, request));
    }
}

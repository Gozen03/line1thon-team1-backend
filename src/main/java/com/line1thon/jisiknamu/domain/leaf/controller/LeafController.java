package com.line1thon.jisiknamu.domain.leaf.controller;

import com.line1thon.jisiknamu.domain.leaf.dto.LeafDetailResponse;
import com.line1thon.jisiknamu.domain.leaf.dto.ReviewListResponse;
import com.line1thon.jisiknamu.domain.leaf.dto.ReviewQuizResponse;
import com.line1thon.jisiknamu.domain.leaf.dto.TreeReviewListResponse;
import com.line1thon.jisiknamu.domain.leaf.service.LeafService;
import com.line1thon.jisiknamu.domain.leaf.service.ReviewService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "잎·복습")
@RestController
@RequiredArgsConstructor
public class LeafController {

    private final LeafService leafService;
    private final ReviewService reviewService;

    @Operation(summary = "L2. 잎 상세 (그날 기록)", description = """
            잎을 눌렀을 때 그날의 공부 내용, 문제와 내 답, 피드백을 줍니다. 잎눈도 같은 API를 씁니다.

            - `questions`는 잎의 등급을 정한 마지막 첫 풀이·재도전 세트의 7문제입니다. 화면은 틀린 문제를 먼저 2개 보여 주고 "7문제 모두 보기"로 펼칩니다.
            - `reviewed`·`reviewedAt`은 **복습에 성공했을 때만** 값이 있습니다. 실패한 복습은 `quizSets`에만 남습니다.
            - `feedback.summary`는 복습에 성공했으면 복습 결과까지 반영한 총평입니다.
            - `feedback.weaknesses`에는 복습으로 이해한 주제도 기록으로 남습니다.
            - `imageUrls`는 10분 뒤 만료됩니다. 저장하지 말고 화면을 열 때마다 받은 주소를 씁니다.
            - 화면을 열 때 광고 A1(`RECORD_VIEW`)을 함께 호출합니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.LEAF_NOT_FOUND})
    @GetMapping("/api/v1/leaves/{leafId}")
    public ApiResponse<LeafDetailResponse> getLeaf(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "잎 ID", example = "13") @PathVariable Long leafId) {
        return ApiResponse.success(leafService.getLeaf(userId, leafId));
    }

    @Operation(summary = "R0. 전체 복습 목록", description = """
            하단 "복습" 탭입니다. 모든 나무에서 **지금 복습할 수 있는** 잎을 오래된 공부부터 줍니다.

            - `reviews`가 비면 "지금 복습할 잎이 없어요. 다음 복습: 11월 5일"처럼 `nextReviewAt`으로 안내합니다.
            - `nextReviewAt`도 `null`이면 "복습할 잎이 없어요"만 보여 줍니다.
            - "복습 시작하기"는 목록의 첫 잎부터 **R2**를 부릅니다.
            """)
    @GetMapping("/api/v1/reviews")
    public ApiResponse<ReviewListResponse> getReviews(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(reviewService.getReviews(userId));
    }

    @Operation(summary = "R1. 나무별 복습할 잎 목록", description = """
            이 나무에서 지금 복습할 수 있는 잎만 오래된 공부부터 줍니다. 비어 있으면 복습 알림 화면 대신 나무 화면을 보여 줍니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.TREE_NOT_FOUND})
    @GetMapping("/api/v1/trees/{treeId}/reviews")
    public ApiResponse<TreeReviewListResponse> getTreeReviews(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나무 ID", example = "1") @PathVariable Long treeId) {
        return ApiResponse.success(reviewService.getTreeReviews(userId, treeId));
    }

    @Operation(summary = "R2. 복습 3문제 출제", description = """
            요청 본문이 없습니다. 단풍잎·마른 잎이고 복습 시점이 됐을 때만 쓸 수 있습니다. 항상 객관식 3문제입니다.

            - 답은 **S2**로 제출합니다 (3개).
            - 제출하지 않은 복습 세트가 있으면 새로 만들지 않고 그 세트를 다시 줍니다 (복습 중 앱을 닫은 경우).
            - 복습에 실패해 다시 풀 때는 이전 복습과 다른 새 문제를 냅니다.
            - `REQUEST_IN_PROGRESS`는 버튼을 두 번 누른 경우이므로 안내 없이 무시합니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.LEAF_NOT_FOUND, ErrorCode.REVIEW_NOT_AVAILABLE,
            ErrorCode.REQUEST_IN_PROGRESS, ErrorCode.AI_SERVER_ERROR, ErrorCode.AI_TIMEOUT})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/leaves/{leafId}/reviews")
    public ApiResponse<ReviewQuizResponse> createReview(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "잎 ID", example = "13") @PathVariable Long leafId) {
        return ApiResponse.success(reviewService.createReview(userId, leafId));
    }
}

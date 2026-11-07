package com.line1thon.jisiknamu.domain.tree.controller;

import com.line1thon.jisiknamu.domain.tree.dto.ForestResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeDetailResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateResponse;
import com.line1thon.jisiknamu.domain.tree.service.TreeService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "나무")
@RestController
@RequestMapping("/api/v1/trees")
@RequiredArgsConstructor
public class TreeController {

    private final TreeService treeService;

    @Operation(summary = "T1. 숲 조회", description = """
            숲 화면의 나무 카드와 "무료 나무 3 / 3" 표시에 씁니다. 나무는 만든 순서대로 줍니다.
            """)
    @GetMapping
    public ApiResponse<ForestResponse> getForest(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(treeService.getForest(userId));
    }

    @Operation(summary = "T2. 나무 추가", description = """
            - 지금 가진 나무가 3그루면 `TREE_LIMIT_EXCEEDED`입니다. 나무를 지우면 자리가 돌아옵니다.
            - 프론트는 이때 "추가 나무 구매하고 심기" 버튼을 보여 주되, 결제는 추후 개발이라 안내만 띄웁니다.
            - `treeShape`가 `BASIC`이 아니면 `TREE_SHAPE_LOCKED`입니다.
            - 세 필드 모두 필수입니다. 빠지거나 없는 enum 값이면 `INVALID_REQUEST`입니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.TREE_LIMIT_EXCEEDED, ErrorCode.TREE_SHAPE_LOCKED})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<TreeCreateResponse> createTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody TreeCreateRequest request) {
        return ApiResponse.success(treeService.createTree(userId, request));
    }

    @Operation(summary = "T3. 나무 상세", description = """
            나무 배치 화면과 가지 확대 화면이 같은 응답을 씁니다.

            - 잎은 `position` 오름차순입니다. 프론트가 `position`으로 가지를 나눕니다.
            - 가지별 잎: 1번 `1~5` / 2번 `6~12` / 3번 `13~19` / 4번 `20~24` (왼쪽부터 5·7·7·5개)
            - `pendingQuizSetId`에 값이 있으면 "풀던 문제가 있어요. 이어서 풀까요?"를 띄우고 **S4**로 문제를 받습니다.
            - `grade`와 `originalGrade`가 다르면 복습으로 등급이 바뀐 잎입니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.TREE_NOT_FOUND})
    @GetMapping("/{treeId}")
    public ApiResponse<TreeDetailResponse> getTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나무 ID", example = "1") @PathVariable Long treeId) {
        return ApiResponse.success(treeService.getTree(userId, treeId));
    }

    @Operation(summary = "T4. 나무 수정", description = """
            나무 화면 메뉴(⋯)의 "나무 수정"입니다. **보낸 필드만** 바뀌고, `{}`를 보내면 그대로 200입니다.

            - 수정 화면에 "이름 고치기용이에요. 다른 과목은 새 나무를 만들어 주세요."를 보여 주세요. 서버는 막지 않습니다.
            - 바뀐 값은 새 공부(S1)부터 적용됩니다. 이미 만든 기록의 재도전·복습은 공부할 때의 값으로 출제합니다.
            - 나무 모양은 바꿀 수 없습니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.TREE_NOT_FOUND})
    @PatchMapping("/{treeId}")
    public ApiResponse<TreeUpdateResponse> updateTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나무 ID", example = "1") @PathVariable Long treeId,
            @RequestBody TreeUpdateRequest request) {
        return ApiResponse.success(treeService.updateTree(userId, treeId, request));
    }

    @Operation(summary = "T5. 나무 삭제", description = """
            응답 본문이 없습니다 (204). 잎·공부 기록·문제 세트·사진을 모두 지우고 무료 나무 자리를 돌려줍니다. **되돌릴 수 없습니다.**

            삭제 전에 프론트가 확인 팝업을 띄웁니다: "수학 나무를 삭제할까요? 잎 13개와 공부 기록이 모두 사라지고 되돌릴 수 없어요." (이름·잎 수는 T3 응답 사용)
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.TREE_NOT_FOUND})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping("/{treeId}")
    public void deleteTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나무 ID", example = "1") @PathVariable Long treeId) {
        treeService.deleteTree(userId, treeId);
    }
}

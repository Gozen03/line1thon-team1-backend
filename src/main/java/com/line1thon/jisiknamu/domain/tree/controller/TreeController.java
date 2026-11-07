package com.line1thon.jisiknamu.domain.tree.controller;

import com.line1thon.jisiknamu.domain.tree.dto.ForestResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeCreateResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeDetailResponse;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateRequest;
import com.line1thon.jisiknamu.domain.tree.dto.TreeUpdateResponse;
import com.line1thon.jisiknamu.domain.tree.service.TreeService;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "나무")
@RestController
@RequestMapping("/api/v1/trees")
@RequiredArgsConstructor
public class TreeController {

    private final TreeService treeService;

    @Operation(summary = "T1. 숲 조회 (나무 목록)")
    @GetMapping
    public ApiResponse<ForestResponse> getForest(@Parameter(hidden = true) @AuthenticationPrincipal Long userId) {
        return ApiResponse.success(treeService.getForest(userId));
    }

    @Operation(summary = "T2. 나무 추가")
    @PostMapping
    public ResponseEntity<ApiResponse<TreeCreateResponse>> createTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @RequestBody TreeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(treeService.createTree(userId, request)));
    }

    @Operation(summary = "T3. 나무 상세 (잎 배치, 풀던 문제 확인)")
    @GetMapping("/{treeId}")
    public ApiResponse<TreeDetailResponse> getTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long treeId) {
        return ApiResponse.success(treeService.getTree(userId, treeId));
    }

    @Operation(summary = "T4. 나무 수정 (과목 이름·공부 수준, 보낸 필드만 바뀜)")
    @PatchMapping("/{treeId}")
    public ApiResponse<TreeUpdateResponse> updateTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long treeId,
            @RequestBody TreeUpdateRequest request) {
        return ApiResponse.success(treeService.updateTree(userId, treeId, request));
    }

    @Operation(summary = "T5. 나무 삭제 (기록 전부 삭제, 무료 자리 반환)")
    @DeleteMapping("/{treeId}")
    public ResponseEntity<Void> deleteTree(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long treeId) {
        treeService.deleteTree(userId, treeId);
        return ResponseEntity.noContent().build();
    }
}

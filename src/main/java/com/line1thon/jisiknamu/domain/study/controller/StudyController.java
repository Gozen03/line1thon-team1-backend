package com.line1thon.jisiknamu.domain.study.controller;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.study.dto.StudyCreateRequest;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.service.StudyService;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "공부·문제")
@RestController
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @Operation(summary = "S1. 공부 입력 + 7문제 출제")
    @PostMapping(value = "/api/v1/trees/{treeId}/studies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<QuizSetResponse>> createStudy(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long treeId,
            // 값 검사는 명세의 에러 코드로 응답하도록 서비스에서 한다
            @RequestParam(required = false) InputType inputType,
            @RequestParam(required = false) List<MultipartFile> images,
            @RequestParam(required = false) String content,
            @RequestParam(required = false) String memo) {
        QuizSetResponse response = studyService.createStudy(userId, treeId,
                new StudyCreateRequest(inputType, images, content, memo));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }
}

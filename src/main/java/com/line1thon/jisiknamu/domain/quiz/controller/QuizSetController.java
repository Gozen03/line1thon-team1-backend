package com.line1thon.jisiknamu.domain.quiz.controller;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.service.QuizSetService;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공부·문제")
@RestController
@RequestMapping("/api/v1/quiz-sets")
@RequiredArgsConstructor
public class QuizSetController {

    private final QuizSetService quizSetService;

    @Operation(summary = "S4. 제출 전 문제 다시 받기 (정답 없음)")
    @GetMapping("/{quizSetId}")
    public ApiResponse<QuizSetResponse> getQuizSet(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @PathVariable Long quizSetId) {
        return ApiResponse.success(quizSetService.getQuizSet(userId, quizSetId));
    }
}

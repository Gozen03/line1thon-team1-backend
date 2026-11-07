package com.line1thon.jisiknamu.domain.quiz.controller;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.service.QuizSetService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
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

    @Operation(summary = "S4. 제출 전 문제 다시 받기", description = """
            문제를 풀다가 앱을 닫았을 때 같은 문제를 다시 받습니다. `quizSetId`는 T3의 `pendingQuizSetId`로 얻습니다.

            - 푼 답은 서버에 저장하지 않으므로 1번부터 다시 풉니다.
            - 정답은 주지 않습니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.QUIZ_SET_NOT_FOUND, ErrorCode.ALREADY_SUBMITTED})
    @GetMapping("/{quizSetId}")
    public ApiResponse<QuizSetResponse> getQuizSet(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "문제 세트 ID", example = "301") @PathVariable Long quizSetId) {
        return ApiResponse.success(quizSetService.getQuizSet(userId, quizSetId));
    }
}

package com.line1thon.jisiknamu.domain.quiz.controller;

import com.line1thon.jisiknamu.domain.quiz.dto.AnswerSubmitRequest;
import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.dto.SubmissionResult;
import com.line1thon.jisiknamu.domain.quiz.service.QuizSetService;
import com.line1thon.jisiknamu.domain.quiz.service.SubmissionService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "공부·문제")
@RestController
@RequestMapping("/api/v1/quiz-sets")
@RequiredArgsConstructor
public class QuizSetController {

    private final QuizSetService quizSetService;
    private final SubmissionService submissionService;

    @Operation(summary = "S4. 제출 전 문제 다시 받기", description = """
            문제를 풀다가 앱을 닫았을 때 같은 문제를 다시 받습니다. `quizSetId`는 T3의 `pendingQuizSetId`(첫 풀이·재도전) 또는 R2(복습)로 얻습니다.

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

    @Operation(summary = "S2. 답 제출 + 채점 + 잎 생성·변경", description = """
            문제를 다 푼 뒤 **문제 수만큼** 한 번에 보냅니다 (첫 풀이·재도전 7개, 복습 3개). 응답 형태는 `quizSetType`에 따라 다릅니다.

            **답**
            - 객관식: `selectedIndex` 0~3. 안 푼 문제는 `null`이고 오답입니다.
            - 서술형: `essayText` 1,000자 이하. 비어 있으면 피드백이 "답을 적지 않았어요..."입니다.

            **잎**
            - 등급은 서술형을 뺀 6문제의 맞힌 개수로 정합니다: 6 `GREEN`, 5~4 `LIGHT_GREEN`, 3~2 `AUTUMN`, 1 `BROWN`, 0 `BUD`.
            - 첫 풀이는 맞힌 개수와 상관없이 잎이 생기고, 나무의 다음 `position`에 붙습니다. 앱을 닫아도 저장되어 있습니다.
            - 재도전은 새 잎을 만들지 않고 같은 자리 잎의 등급과 주제 목록을 바꿉니다.
            - `AUTUMN`·`BROWN`이면 `reviewTarget`이 `true`이고 `reviewAvailableAt`에 복습 가능 시각이 있습니다.

            **복습 (`REVIEW`)**
            - 3문제를 모두 맞히면 `passed`가 `true`이고, 잎이 `GREEN`이 되며 약한 주제가 모두 `recoveredTopics`로 바뀝니다. 총평도 다시 씁니다.
            - 실패하면 등급과 잎의 총평은 그대로이고 `summary`는 응원 문구입니다. `leaf.reviewAvailableAt` 뒤에 새 3문제로 다시 복습합니다.
            - "남은 복습 잎 2개"는 `remainingReviewCount`를 씁니다.

            **화면**
            - 배치 팝업 "수학 나무 · 13번째 잎 · 단풍잎"은 `leaf.position`, `leaf.grade`와 나무 이름으로 만듭니다.
            - 꽃봉오리 화면의 "먼저 다시 볼 부분"은 `feedback.weaknesses`를 씁니다.
            - AI 오류가 나면 저장하지 않으므로 같은 답으로 다시 제출할 수 있습니다.
            - `REQUEST_IN_PROGRESS`는 버튼을 두 번 누른 경우이므로 안내 없이 무시합니다.

            AI 에러를 확인하려면 서술형 답에 `[mock:error]` 또는 `[mock:timeout]`을 넣으세요.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.ANSWER_COUNT_MISMATCH, ErrorCode.FORBIDDEN, ErrorCode.QUIZ_SET_NOT_FOUND,
            ErrorCode.ALREADY_SUBMITTED, ErrorCode.REQUEST_IN_PROGRESS, ErrorCode.NOT_BUD_RESULT, ErrorCode.TREE_FULL,
            ErrorCode.AI_SERVER_ERROR, ErrorCode.AI_TIMEOUT})
    @PostMapping("/{quizSetId}/answers")
    public ApiResponse<SubmissionResult> submitAnswers(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "문제 세트 ID", example = "301") @PathVariable Long quizSetId,
            @RequestBody AnswerSubmitRequest request) {
        return ApiResponse.success(submissionService.submit(userId, quizSetId, request));
    }
}

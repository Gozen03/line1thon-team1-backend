package com.line1thon.jisiknamu.domain.study.controller;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.study.dto.StudyCreateRequest;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.service.StudyService;
import com.line1thon.jisiknamu.global.config.ApiErrors;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "공부·문제")
@RestController
@RequiredArgsConstructor
public class StudyController {

    private final StudyService studyService;

    @Operation(summary = "S1. 공부 입력 + 7문제 출제", description = """
            `multipart/form-data`로 보냅니다. 문제는 항상 7개입니다: 쉬움 1·2번, 보통 3·4번, 어려움 5·6번, 서술형 7번.

            **입력**
            - `inputType=TEXT`: `content`가 필수입니다. 앞뒤 공백을 지운 뒤 20~5,000자입니다.
            - `inputType=IMAGE`: `images`가 필수입니다. 1~5장, 장당 10MB 이하, 확장자 jpg·jpeg·png·webp·heic입니다.
            - 받은 입력 방식의 값만 씁니다 (`IMAGE`인데 `content`를 보내면 무시).

            **동작**
            - 같은 나무에 제출하지 않은 첫 풀이·재도전 세트가 있으면 지웁니다. 나무마다 풀던 세트는 최대 1개입니다.
            - 에러가 나면 공부 기록을 저장하지 않습니다. 프론트는 입력값을 그대로 두고 다시 시도하게 합니다.
            - 시간이 오래 걸릴 수 있으니 로딩 화면을 띄우고 타임아웃을 60초로 둡니다.

            **에러별 화면**
            - `NOTE_UNREADABLE`: "더 밝게 다시 찍어 주세요"를 안내합니다.
            - `TREE_FULL`: "나무가 다 자랐어요. 새 나무에서 이어서 공부해 주세요."를 보여 줍니다.
            - `REQUEST_IN_PROGRESS`: 버튼을 두 번 누른 경우이므로 안내 없이 무시합니다.

            AI 연결 전이라 지금은 가짜 문제를 줍니다. 에러를 확인하는 방법은 맨 위 **AI Mock**을 보세요.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.STUDY_INPUT_EMPTY, ErrorCode.IMAGE_INVALID, ErrorCode.FORBIDDEN,
            ErrorCode.TREE_NOT_FOUND, ErrorCode.TREE_FULL, ErrorCode.REQUEST_IN_PROGRESS, ErrorCode.NOTE_UNREADABLE,
            ErrorCode.CONTENT_TOO_SHORT, ErrorCode.AI_SERVER_ERROR, ErrorCode.AI_TIMEOUT})
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE, schema = @Schema(implementation = StudyForm.class)))
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = "/api/v1/trees/{treeId}/studies", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<QuizSetResponse> createStudy(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "나무 ID", example = "1") @PathVariable Long treeId,
            // 값 검사는 명세의 에러 코드로 응답하도록 서비스에서 한다. Swagger에는 StudyForm으로 보여 준다
            @Parameter(hidden = true) @RequestParam(required = false) InputType inputType,
            @Parameter(hidden = true) @RequestParam(required = false) List<MultipartFile> images,
            @Parameter(hidden = true) @RequestParam(required = false) String content,
            @Parameter(hidden = true) @RequestParam(required = false) String memo) {
        return ApiResponse.success(studyService.createStudy(userId, treeId,
                new StudyCreateRequest(inputType, images, content, memo)));
    }

    @Operation(summary = "S3. 꽃봉오리 재도전 (새 7문제)", description = """
            요청 본문이 없습니다. 이 공부의 잎이 꽃봉오리(`BUD`)일 때만 쓸 수 있고 횟수 제한은 없습니다.

            - 저장해 둔 공부 내용과, 공부할 때의 과목 이름·공부 수준으로 출제합니다. 사진을 다시 올리지 않습니다.
            - 약한 주제를 쉬움·보통 문제에 더 반영하고, 이전 세트와 같은 문제는 내지 않습니다.
            - 이전에 제출한 세트는 기록으로 남습니다.
            - 같은 나무에 제출하지 않은 첫 풀이·재도전 세트가 있으면 지우고 새로 만듭니다.
            - 답은 **S2**로 제출하고, `quizSetType`은 `RETRY`입니다.
            """)
    @ApiErrors({ErrorCode.INVALID_REQUEST, ErrorCode.FORBIDDEN, ErrorCode.STUDY_NOT_FOUND, ErrorCode.NOT_BUD_RESULT,
            ErrorCode.REQUEST_IN_PROGRESS, ErrorCode.AI_SERVER_ERROR, ErrorCode.AI_TIMEOUT})
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/api/v1/studies/{studyId}/retry")
    public ApiResponse<QuizSetResponse> retry(
            @Parameter(hidden = true) @AuthenticationPrincipal Long userId,
            @Parameter(description = "공부 기록 ID", example = "31") @PathVariable Long studyId) {
        return ApiResponse.success(studyService.createRetry(userId, studyId));
    }

    /**
     * Swagger에 multipart 폼을 보여 주기 위한 형식. 실제 값은 위 @RequestParam으로 받는다.
     */
    @Schema(name = "StudyForm")
    private record StudyForm(
            @Schema(description = "입력 방식: 노트 사진 / 텍스트", requiredMode = Schema.RequiredMode.REQUIRED)
            InputType inputType,
            @ArraySchema(arraySchema = @Schema(description = "노트 사진 1~5장. IMAGE일 때 필수"),
                    schema = @Schema(type = "string", format = "binary"))
            List<String> images,
            @Schema(description = "공부한 내용 20~5,000자. TEXT일 때 필수", example = "이차함수의 꼭짓점과 판별식을 공부했다. 꼭짓점 공식은 x = -b/2a 이다.")
            String content,
            @Schema(description = "메모. 선택, 최대 200자", example = "판별식이 헷갈림")
            String memo) {
    }
}

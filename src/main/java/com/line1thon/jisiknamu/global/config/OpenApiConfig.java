package com.line1thon.jisiknamu.global.config;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI: /swagger-ui/index.html, 문서 JSON: /v3/api-docs
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_AUTH = "bearerAuth";

    private static final String GUIDE = """
            프론트 ↔ 메인 서버 API입니다. 왼쪽 위 주소가 서버 주소이고, 모든 경로 앞에 `/api/v1`이 붙습니다.

            ### 빠르게 해 보기
            1. **U1**로 가입하고 응답의 `accessToken`을 복사합니다.
            2. 오른쪽 위 **Authorize**에 토큰만 붙여 넣습니다 (`Bearer`는 붙이지 않습니다).
            3. 다른 API의 **Try it out**을 누르고 **Execute**로 실행합니다.

            ### 인증
            - 헤더: `Authorization: Bearer {accessToken}`
            - 토큰이 필요 없는 API는 **U1 가입**, **U2 로그인**뿐입니다.
            - 토큰은 30일 유효하고 재발급 API는 없습니다. 프론트는 `localStorage`에 저장하고, 401을 받으면 시작하기 화면으로 보냅니다.

            ### 응답 형식
            204를 뺀 모든 응답은 아래 형식입니다. 에러는 HTTP 상태와 `error.code`로 구분합니다.
            ```json
            { "success": true, "data": { }, "error": null }
            { "success": false, "data": null, "error": { "code": "TREE_FULL", "message": "..." } }
            ```

            ### 공통 규칙
            - 필드 이름은 camelCase, ID는 숫자입니다.
            - 시간은 한국 시간 ISO 8601입니다. 예: `2026-11-15T14:30:00+09:00`
            - 목록은 페이지 없이 전체를 줍니다.
            - 문제 응답에는 정답·해설·모범답안이 없습니다.
            - S1·S2·S3·R2는 AI를 기다리므로 로딩 화면을 띄우고, 프론트 타임아웃을 60초로 둡니다.
            - `REQUEST_IN_PROGRESS`(409)는 버튼을 두 번 누른 경우입니다. 안내 없이 무시합니다.

            ### 지금 없는 API
            A1(광고)은 아직 만드는 중입니다.

            ### AI Mock
            AI 서버 연결 전이라 S1·S2·S3·R2는 `(Mock)`이 붙은 가짜 문제와 피드백을 줍니다. 응답은 약 3초 뒤에 옵니다.
            정답은 S2 응답의 `answerIndex`로 확인할 수 있습니다. 아래 값을 넣으면 에러 화면을 확인할 수 있습니다.

            | 값 | 넣는 곳 | 결과 |
            | --- | --- | --- |
            | `[mock:unreadable]` | S1 메모 | `NOTE_UNREADABLE` (422) |
            | `[mock:short]` | S1 메모 | `CONTENT_TOO_SHORT` (422) |
            | `[mock:error]` | S1 메모, S2 서술형 답 | `AI_SERVER_ERROR` (502) |
            | `[mock:timeout]` | S1 메모, S2 서술형 답 | `AI_TIMEOUT` (504) |

            ### 에러 코드 전체
            """;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("지식 나무 키우기 메인 서버 API").version("v2.1").description(GUIDE + errorCodeTable()))
                .addTagsItem(new Tag().name("회원").description("가입·로그인·내 정보. 닉네임과 숫자 비밀번호로 로그인합니다."))
                .addTagsItem(new Tag().name("나무").description("과목 하나가 나무 한 그루입니다. 무료 3그루, 나무마다 잎 24개까지입니다."))
                .addTagsItem(new Tag().name("공부·문제").description("공부 내용을 입력하면 7문제(객관식 6 + 서술형 1)를 받습니다."))
                .addTagsItem(new Tag().name("잎·복습").description("지난 기록을 보고, 단풍잎·갈색잎을 3문제 복습으로 되살립니다."))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    public OperationCustomizer apiErrorsCustomizer() {
        return (operation, handlerMethod) -> {
            List<ErrorCode> errorCodes = new ArrayList<>();
            // @SecurityRequirements가 붙은 API(가입·로그인)만 토큰이 필요 없다
            if (!handlerMethod.hasMethodAnnotation(SecurityRequirements.class)) {
                errorCodes.add(ErrorCode.UNAUTHORIZED);
            }
            ApiErrors apiErrors = handlerMethod.getMethodAnnotation(ApiErrors.class);
            if (apiErrors != null) {
                errorCodes.addAll(Arrays.asList(apiErrors.value()));
            }
            Map<Integer, List<ErrorCode>> byStatus = errorCodes.stream()
                    .collect(Collectors.groupingBy(code -> code.getStatus().value(), TreeMap::new, Collectors.toList()));
            byStatus.forEach((status, codes) ->
                    operation.getResponses().addApiResponse(String.valueOf(status), toApiResponse(codes)));
            return operation;
        };
    }

    /**
     * 공통 응답 스키마는 성공 응답에만 쓰인다 (에러는 toApiResponse의 예시로 보여 준다). 성공일 때 error는 null이다.
     */
    @Bean
    public OpenApiCustomizer successResponseCustomizer() {
        return openApi -> {
            openApi.getComponents().getSchemas().forEach((name, schema) -> {
                if (name.startsWith("ApiResponse")) {
                    schema.getProperties().put("error", new JsonSchema().types(Set.of("null")).description("성공이면 null"));
                }
            });
            openApi.getComponents().getSchemas().remove("ErrorBody");
        };
    }

    private ApiResponse toApiResponse(List<ErrorCode> codes) {
        MediaType mediaType = new MediaType();
        codes.forEach(code -> mediaType.addExamples(code.name(), new Example()
                .summary(code.name())
                .value(errorExample(code))));
        return new ApiResponse()
                .description(codes.stream().map(code -> "`" + code.name() + "`").collect(Collectors.joining(", ")))
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType));
    }

    // Swagger 문서 라이브러리는 Jackson 2로 직렬화한다. JsonNode여야 data의 null이 빠지지 않는다
    private ObjectNode errorExample(ErrorCode code) {
        JsonNodeFactory json = JsonNodeFactory.instance;
        ObjectNode error = json.objectNode().put("code", code.name()).put("message", code.getMessage());
        ObjectNode example = json.objectNode().put("success", false);
        example.set("data", json.nullNode());
        example.set("error", error);
        return example;
    }

    private String errorCodeTable() {
        return "| code | HTTP | message |\n| --- | --- | --- |\n" + Arrays.stream(ErrorCode.values())
                .map(code -> "| `%s` | %d | %s |".formatted(code.name(), code.getStatus().value(), code.getMessage()))
                .collect(Collectors.joining("\n"));
    }
}

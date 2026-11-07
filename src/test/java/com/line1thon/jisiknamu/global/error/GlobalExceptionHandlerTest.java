package com.line1thon.jisiknamu.global.error;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.line1thon.jisiknamu.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 성공_응답은_data를_담고_error는_null이다() throws Exception {
        expectBody(mockMvc.perform(get("/test/ok")).andExpect(status().isOk()),
                "{\"success\":true,\"data\":{\"treeId\":1},\"error\":null}");
    }

    @Test
    void 비즈니스_예외는_명세의_코드와_상태로_응답한다() throws Exception {
        expectBody(mockMvc.perform(get("/test/business")).andExpect(status().isForbidden()),
                errorJson("TREE_LIMIT_EXCEEDED", "무료 나무는 3그루까지입니다."));
    }

    @Test
    void 검증_실패는_INVALID_REQUEST와_필드_메시지로_응답한다() throws Exception {
        expectBody(mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                        .andExpect(status().isBadRequest()),
                errorJson("INVALID_REQUEST", "이름을 입력해 주세요."));
    }

    @Test
    void JSON_형식_오류는_INVALID_REQUEST로_응답한다() throws Exception {
        expectBody(mockMvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{깨진 JSON"))
                        .andExpect(status().isBadRequest()),
                errorJson("INVALID_REQUEST", ErrorCode.INVALID_REQUEST.getMessage()));
    }

    @Test
    void 없는_경로는_404에_공통_형식으로_응답한다() throws Exception {
        expectBody(mockMvc.perform(get("/test/none")).andExpect(status().isNotFound()),
                errorJson("INVALID_REQUEST", "요청한 경로를 찾을 수 없습니다."));
    }

    @Test
    void 지원하지_않는_메서드는_405에_공통_형식으로_응답한다() throws Exception {
        expectBody(mockMvc.perform(post("/test/ok")).andExpect(status().isMethodNotAllowed()),
                errorJson("INVALID_REQUEST", "지원하지 않는 메서드입니다."));
    }

    @Test
    void 업로드_용량_초과는_IMAGE_INVALID_400으로_응답한다() throws Exception {
        expectBody(mockMvc.perform(get("/test/too-large")).andExpect(status().isBadRequest()),
                errorJson("IMAGE_INVALID", ErrorCode.IMAGE_INVALID.getMessage()));
    }

    @Test
    void 예상_못한_예외는_내부_메시지를_숨기고_500으로_응답한다() throws Exception {
        expectBody(mockMvc.perform(get("/test/unexpected")).andExpect(status().isInternalServerError()),
                errorJson("INTERNAL_ERROR", ErrorCode.INTERNAL_ERROR.getMessage()));
    }

    private void expectBody(ResultActions result, String json) throws Exception {
        result.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(json, JsonCompareMode.STRICT));
    }

    private String errorJson(String code, String message) {
        return "{\"success\":false,\"data\":null,\"error\":{\"code\":\"%s\",\"message\":\"%s\"}}".formatted(code, message);
    }

    record TestRequest(@NotBlank(message = "이름을 입력해 주세요.") String name) {
    }

    @RestController
    static class TestController {

        @GetMapping("/test/ok")
        ApiResponse<Map<String, Long>> ok() {
            return ApiResponse.success(Map.of("treeId", 1L));
        }

        @GetMapping("/test/business")
        void business() {
            throw new BusinessException(ErrorCode.TREE_LIMIT_EXCEEDED);
        }

        @PostMapping("/test/valid")
        void valid(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/too-large")
        void tooLarge() {
            throw new MaxUploadSizeExceededException(1L);
        }

        @GetMapping("/test/unexpected")
        void unexpected() {
            throw new IllegalStateException("내부 정보");
        }
    }
}

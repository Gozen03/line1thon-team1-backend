package com.line1thon.jisiknamu.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerDocsTest {

    private static final String USERS = "$.paths['/api/v1/users']";
    private static final String ME = "$.paths['/api/v1/users/me']";
    private static final String TREE = "$.paths['/api/v1/trees/{treeId}']";
    private static final String STUDIES = "$.paths['/api/v1/trees/{treeId}/studies']";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions docs() throws Exception {
        return mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }

    @Test
    void 성공_상태를_실제_코드로_보여_준다() throws Exception {
        docs().andExpect(jsonPath(USERS + ".post.responses.201").exists())
                .andExpect(jsonPath(USERS + ".post.responses.200").doesNotExist())
                .andExpect(jsonPath(ME + ".delete.responses.204").exists())
                .andExpect(jsonPath(ME + ".delete.responses.200").doesNotExist())
                .andExpect(jsonPath(STUDIES + ".post.responses.201").exists())
                .andExpect(jsonPath(TREE + ".get.responses.200").exists());
    }

    @Test
    void 에러_코드를_상태별_예시로_보여_주고_맨_위에_전체_표가_있다() throws Exception {
        docs().andExpect(jsonPath(USERS + ".post.responses.409.content['application/json'].examples.NICKNAME_DUPLICATED").exists())
                .andExpect(jsonPath(STUDIES + ".post.responses.422.content['application/json'].examples.NOTE_UNREADABLE").exists())
                .andExpect(jsonPath(STUDIES + ".post.responses.422.content['application/json'].examples.CONTENT_TOO_SHORT").exists())
                .andExpect(jsonPath("$.info.description", containsString("`TREE_LIMIT_EXCEEDED` | 403")));
    }

    @Test
    void 에러_예시는_실제_응답과_같은_JSON이고_성공_응답의_error는_null이다() throws Exception {
        String example = USERS + ".post.responses.409.content['application/json'].examples.NICKNAME_DUPLICATED.value";
        docs().andExpect(jsonPath(example + ".success").value(false))
                .andExpect(jsonPath(example + ".data").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath(example + ".error.code").value("NICKNAME_DUPLICATED"))
                .andExpect(jsonPath("$.components.schemas.ApiResponseAuthResponse.properties.error.type").value("null"))
                .andExpect(jsonPath(USERS + ".post.responses.201.content['application/json']").exists());
    }

    @Test
    void S1은_multipart_폼으로_보여_준다() throws Exception {
        String form = STUDIES + ".post.requestBody.content['multipart/form-data'].schema";
        docs().andExpect(jsonPath(form).exists())
                .andExpect(jsonPath("$.components.schemas.StudyForm.properties.images.items.format").value("binary"))
                .andExpect(jsonPath(STUDIES + ".post.parameters[?(@.name == 'content')]", empty()))
                .andExpect(jsonPath(STUDIES + ".post.parameters[?(@.name == 'inputType')]", empty()));
    }

    @Test
    void 토큰이_필요한_API만_401과_자물쇠를_보여_준다() throws Exception {
        docs().andExpect(jsonPath(ME + ".get.responses.401").exists())
                .andExpect(jsonPath(USERS + ".post.responses.401").doesNotExist())
                .andExpect(jsonPath(USERS + ".post.security", empty()));
    }

    @Test
    void 필드_설명과_예시가_있고_userId는_파라미터로_드러나지_않는다() throws Exception {
        String body = docs()
                .andExpect(jsonPath("$.components.schemas.AuthRequest.properties.nickname.description").exists())
                .andExpect(jsonPath("$.components.schemas.AuthRequest.properties.nickname.example").value("재민"))
                .andReturn().getResponse().getContentAsString();
        List<String> params = JsonPath.read(body, "$.paths..parameters[*].name");
        assertThat(params).doesNotContain("userId").contains("treeId", "quizSetId");
    }
}

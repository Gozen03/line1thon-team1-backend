package com.line1thon.jisiknamu.domain.study;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.line1thon.jisiknamu.global.storage.ImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

// 사진은 커밋 뒤에 지우므로 이 테스트만 실제로 커밋한다. 만든 회원은 테스트 끝에 탈퇴로 지운다
@SpringBootTest
@AutoConfigureMockMvc
class ImageCleanupTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ImageStorage imageStorage;

    private String token;
    private Long treeId;

    @BeforeEach
    void 회원과_나무를_만든다() throws Exception {
        when(imageStorage.createReadUrl(anyString())).thenReturn("https://example.invalid/image");
        String signUp = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"사진삭제시험\",\"password\":\"1234\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        token = JsonPath.read(signUp, "$.data.accessToken");
        treeId = createTree();
    }

    @AfterEach
    void 회원을_지운다() throws Exception {
        auth(delete("/api/v1/users/me"));
    }

    @Test
    void 나무를_지우면_커밋_뒤에_사진을_지운다() throws Exception {
        String key = studyWithImage();

        auth(delete("/api/v1/trees/" + treeId)).andExpect(status().isNoContent());

        verify(imageStorage).delete(key);
    }

    @Test
    void 사진_삭제가_실패해도_나무_삭제는_204다() throws Exception {
        String key = studyWithImage();
        doThrow(new RuntimeException("저장소 오류")).when(imageStorage).delete(key);

        auth(delete("/api/v1/trees/" + treeId)).andExpect(status().isNoContent());

        verify(imageStorage).delete(key);
    }

    @Test
    void 탈퇴하면_커밋_뒤에_사진을_지운다() throws Exception {
        String key = studyWithImage();

        auth(delete("/api/v1/users/me")).andExpect(status().isNoContent());

        verify(imageStorage).delete(key);
    }

    @Test
    void 풀던_공부를_새_공부로_바꾸면_이전_사진만_지운다() throws Exception {
        String firstKey = studyWithImage();

        String secondKey = studyWithImage();

        verify(imageStorage).delete(firstKey);
        verify(imageStorage, never()).delete(secondKey);
    }

    private String studyWithImage() throws Exception {
        clearInvocations(imageStorage);
        auth(multipart("/api/v1/trees/" + treeId + "/studies")
                .file(new MockMultipartFile("images", "note.jpg", "image/jpeg", new byte[]{1, 2, 3}))
                .param("inputType", "IMAGE"))
                .andExpect(status().isCreated());
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        verify(imageStorage).upload(key.capture(), any(), anyLong(), anyString());
        return key.getValue();
    }

    private Long createTree() throws Exception {
        String response = auth(post("/api/v1/trees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectName\":\"수학\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.treeId")).longValue();
    }

    private ResultActions auth(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }
}

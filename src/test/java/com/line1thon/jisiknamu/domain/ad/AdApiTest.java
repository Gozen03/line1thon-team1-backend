package com.line1thon.jisiknamu.domain.ad;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

// 실제 Security 필터와 MySQL까지 거쳐 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdApiTest {

    @Autowired
    private MockMvc mockMvc;

    private String token;
    private String otherToken;

    @BeforeEach
    void 회원을_만든다() throws Exception {
        token = signUp("광고보는사람");
        otherToken = signUp("다른사람");
    }

    @Test
    void 첫_번째와_3의_배수_번째에만_광고를_띄운다() throws Exception {
        boolean[] expected = {true, false, true, false, false, true, false, false, true};
        for (int i = 0; i < expected.length; i++) {
            view(token, "LEAF_PLACEMENT")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adPlacement").value("LEAF_PLACEMENT"))
                    .andExpect(jsonPath("$.data.count").value(i + 1))
                    .andExpect(jsonPath("$.data.showAd").value(expected[i]));
        }
    }

    @Test
    void 횟수는_위치별_회원별로_따로_센다() throws Exception {
        view(token, "LEAF_PLACEMENT");
        view(token, "LEAF_PLACEMENT");

        view(token, "RECORD_VIEW")
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.showAd").value(true));
        view(otherToken, "LEAF_PLACEMENT")
                .andExpect(jsonPath("$.data.count").value(1));
        view(token, "LEAF_PLACEMENT")
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.showAd").value(true));
    }

    @Test
    void 위치가_없거나_틀리면_INVALID_REQUEST() throws Exception {
        expectError(mockMvc.perform(post("/api/v1/ads/views").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")), 400, "INVALID_REQUEST");
        expectError(view(token, "BANNER"), 400, "INVALID_REQUEST");
        expectError(mockMvc.perform(post("/api/v1/ads/views")
                .contentType(MediaType.APPLICATION_JSON).content("{\"adPlacement\":\"LEAF_PLACEMENT\"}")), 401, "UNAUTHORIZED");
    }

    private ResultActions view(String bearerToken, String placement) throws Exception {
        return mockMvc.perform(post("/api/v1/ads/views").header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"adPlacement\":\"%s\"}".formatted(placement)));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    private String signUp(String nickname) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"%s\",\"password\":\"1234\"}".formatted(nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }
}

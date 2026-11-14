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
    void 전체_1_4_7_10번째에만_광고를_띄운다() throws Exception {
        boolean[] expected = {true, false, false, true, false, false, true, false, false, true};
        for (int i = 0; i < expected.length; i++) {
            view(token, "LEAF_PLACEMENT")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.adPlacement").value("LEAF_PLACEMENT"))
                    .andExpect(jsonPath("$.data.count").value(i + 1))
                    .andExpect(jsonPath("$.data.showAd").value(expected[i]));
        }
    }

    @Test
    void 잎_배치와_기록_열람을_합쳐서_센다() throws Exception {
        // 배치 1(광고) → 기록 2 → 기록 3 → 배치 4(광고) → 기록 5 → 배치 6 → 기록 7(광고)
        String[] placements = {"LEAF_PLACEMENT", "RECORD_VIEW", "RECORD_VIEW", "LEAF_PLACEMENT",
                "RECORD_VIEW", "LEAF_PLACEMENT", "RECORD_VIEW"};
        boolean[] expected = {true, false, false, true, false, false, true};
        for (int i = 0; i < placements.length; i++) {
            view(token, placements[i])
                    .andExpect(jsonPath("$.data.adPlacement").value(placements[i]))
                    .andExpect(jsonPath("$.data.count").value(i + 1))
                    .andExpect(jsonPath("$.data.showAd").value(expected[i]));
        }
    }

    @Test
    void 두_위치에서_연달아_광고가_나오지_않는다() throws Exception {
        view(token, "LEAF_PLACEMENT").andExpect(jsonPath("$.data.showAd").value(true));
        view(token, "RECORD_VIEW")
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.showAd").value(false));
    }

    @Test
    void 횟수는_회원별로_따로_센다() throws Exception {
        view(token, "LEAF_PLACEMENT");
        view(token, "RECORD_VIEW");

        view(otherToken, "RECORD_VIEW")
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.showAd").value(true));
        view(token, "LEAF_PLACEMENT")
                .andExpect(jsonPath("$.data.count").value(3))
                .andExpect(jsonPath("$.data.showAd").value(false));
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

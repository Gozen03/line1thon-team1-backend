package com.line1thon.jisiknamu.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

// CORS 설정만 보는 테스트라 Security 필터는 끈다. 필터까지 포함한 확인은 UserApiTest에 있다
@WebMvcTest(controllers = CorsConfigTest.NoController.class,
        properties = "cors.allowed-origins=http://localhost:5173,https://front.example.com")
@AutoConfigureMockMvc(addFilters = false)
class CorsConfigTest {

    // 실제 컨트롤러를 올리지 않으려고 지정하는 빈 대상
    static class NoController {
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void 허용한_주소는_쉼표로_구분해_여러_개_받는다() throws Exception {
        preflight("http://localhost:5173").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
        preflight("https://front.example.com").andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://front.example.com"));
    }

    @Test
    void 허용하지_않은_주소는_거부한다() throws Exception {
        preflight("https://evil.example.com").andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private ResultActions preflight(String origin) throws Exception {
        return mockMvc.perform(options("/api/v1/trees")
                .header(HttpHeaders.ORIGIN, origin)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"));
    }
}

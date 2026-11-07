package com.line1thon.jisiknamu.global.config;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(properties = "cors.allowed-origins=http://localhost:5173,https://front.example.com")
class CorsConfigTest {

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

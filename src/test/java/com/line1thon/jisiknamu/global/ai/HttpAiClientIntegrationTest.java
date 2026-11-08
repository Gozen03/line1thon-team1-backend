package com.line1thon.jisiknamu.global.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// AI_MOCK=false일 때 HttpAiClient가 쓰이고, S1이 AI 서버(가짜)를 거쳐 끝까지 동작하는지 확인한다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HttpAiClientIntegrationTest {

    private static final String TEXT = "이차함수의 꼭짓점과 판별식을 공부했다. 스무 글자가 넘는 내용이다.";
    private static final FakeAiServer SERVER = start();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AiClient aiClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private Long treeId;

    private static FakeAiServer start() {
        try {
            return new FakeAiServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void aiServer(DynamicPropertyRegistry registry) {
        registry.add("ai.server.mock", () -> "false");
        registry.add("ai.server.url", SERVER::url);
        registry.add("ai.server.internal-key", () -> "integration-key");
    }

    @AfterAll
    static void stop() {
        SERVER.close();
    }

    @BeforeEach
    void setUp() throws Exception {
        String auth = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"에이아이\",\"password\":\"1234\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        token = JsonPath.read(auth, "$.data.accessToken");
        String tree = mockMvc.perform(post("/api/v1/trees").header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectName\":\"수학\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        treeId = ((Number) JsonPath.read(tree, "$.data.treeId")).longValue();
    }

    @Test
    void AI_MOCK이_false면_HttpAiClient를_쓴다() {
        assertThat(aiClient).isInstanceOf(HttpAiClient.class);
    }

    @Test
    void S1이_AI_서버를_불러_문제를_저장하고_정답은_빼고_준다() throws Exception {
        SERVER.respondSuccess(FakeAiServer.quizData(null));

        mockMvc.perform(multipart("/api/v1/trees/" + treeId + "/studies")
                        .param("inputType", "TEXT").param("content", TEXT).param("memo", "판별식")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.questions[0].content").value("1번 문제"))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").doesNotExist())
                .andExpect(jsonPath("$.data.questions[6].questionType").value("ESSAY"));

        FakeAiServer.Received received = SERVER.last();
        assertThat(received.path()).isEqualTo("/ai/v1/questions");
        assertThat(received.internalKey()).isEqualTo("integration-key");
        JsonNode body = objectMapper.readTree(received.body());
        assertThat(body.get("quizSetType").asString()).isEqualTo("INITIAL");
        assertThat(body.get("subjectName").asString()).isEqualTo("수학");
        assertThat(body.get("inputType").asString()).isEqualTo("TEXT");
        assertThat(body.get("content").asString()).isEqualTo(TEXT);
        assertThat(body.get("memo").asString()).isEqualTo("판별식");
    }

    @Test
    void AI_서버_에러는_명세의_에러_코드로_프론트에_간다() throws Exception {
        SERVER.respondError(422, "CONTENT_TOO_SHORT");

        mockMvc.perform(multipart("/api/v1/trees/" + treeId + "/studies")
                        .param("inputType", "TEXT").param("content", TEXT)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONTENT_TOO_SHORT"));
    }
}

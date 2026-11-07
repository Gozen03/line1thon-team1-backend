package com.line1thon.jisiknamu.domain.study;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.endsWith;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.storage.ImageStorage;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 실제 Security 필터와 MySQL, Mock AI·Mock 저장소로 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class StudyApiTest {

    private static final String TEXT = "이차함수의 꼭짓점과 판별식을 공부했다. 스무 글자가 넘는 내용이다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @MockitoSpyBean
    private AiClient aiClient;

    @MockitoSpyBean
    private ImageStorage imageStorage;

    private String token;
    private String otherToken;
    private Long treeId;

    @BeforeEach
    void 회원과_나무를_만든다() throws Exception {
        token = signUp("공부하는사람");
        otherToken = signUp("다른사람");
        treeId = createTree();
    }

    // S1 텍스트

    @Test
    void 텍스트로_공부하면_정답_없이_7문제를_순서대로_준다() throws Exception {
        ResultActions result = study(text(TEXT).param("memo", " 메모 "));

        result.andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.studyId").isNumber())
                .andExpect(jsonPath("$.data.quizSetId").isNumber())
                .andExpect(jsonPath("$.data.quizSetType").value("INITIAL"))
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.subjectName").value("수학"))
                .andExpect(jsonPath("$.data.studyLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.questions[0].questionId").isNumber())
                .andExpect(jsonPath("$.data.questions[0].number").value(1))
                .andExpect(jsonPath("$.data.questions[0].difficulty").value("EASY"))
                .andExpect(jsonPath("$.data.questions[0].questionType").value("MULTIPLE_CHOICE"))
                .andExpect(jsonPath("$.data.questions[0].options", hasSize(4)))
                .andExpect(jsonPath("$.data.questions[3].difficulty").value("NORMAL"))
                .andExpect(jsonPath("$.data.questions[5].difficulty").value("HARD"))
                .andExpect(jsonPath("$.data.questions[6].number").value(7))
                .andExpect(jsonPath("$.data.questions[6].difficulty").value("ESSAY"))
                .andExpect(jsonPath("$.data.questions[6].questionType").value("ESSAY"))
                .andExpect(jsonPath("$.data.questions[6].options").value(nullValue()))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.questions[6].modelAnswer").doesNotExist())
                .andExpect(jsonPath("$.data.questions[6].gradingPoints").doesNotExist());
    }

    @Test
    void 공부_기록과_정답을_저장하고_풀던_문제로_보인다() throws Exception {
        String body = study(text(TEXT).param("memo", " 메모 ")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();

        Study saved = em.find(Study.class, longAt(body, "$.data.studyId"));
        assertThat(saved.getInputType()).isEqualTo(InputType.TEXT);
        assertThat(saved.getTextContent()).isEqualTo(TEXT);
        assertThat(saved.getMemo()).isEqualTo("메모");
        assertThat(saved.getSubjectName()).isEqualTo("수학");
        assertThat(saved.getStudyLevel()).isEqualTo(StudyLevel.HIGH);

        Long quizSetId = longAt(body, "$.data.quizSetId");
        QuizSet quizSet = em.find(QuizSet.class, quizSetId);
        assertThat(quizSet.getType()).isEqualTo(QuizSetType.INITIAL);
        assertThat(quizSet.getGradedCount()).isEqualTo(6);

        Question first = em.find(Question.class, longAt(body, "$.data.questions[0].questionId"));
        assertThat(first.getAnswerIndex()).isNotNull();
        assertThat(first.getExplanation()).isNotBlank();
        Question essay = em.find(Question.class, longAt(body, "$.data.questions[6].questionId"));
        assertThat(essay.getOptions()).isNull();
        assertThat(essay.getModelAnswer()).isNotBlank();
        assertThat(essay.getGradingPoints()).isNotBlank();

        auth(get("/api/v1/trees/" + treeId)).andExpect(jsonPath("$.data.pendingQuizSetId").value(quizSetId));
        auth(get("/api/v1/trees")).andExpect(jsonPath("$.data.trees[0].lastStudiedAt").isString());
    }

    // S1 사진

    @Test
    void 사진으로_공부하면_저장소에_올리고_읽은_내용을_저장한다() throws Exception {
        String body = study(images(image("a.JPG"), image("b.png"), image("c.webp"), image("d.heic"), image("e.jpeg")))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();

        verify(imageStorage, times(5)).upload(anyString(), any(), anyLong(), anyString());
        // 확장자는 소문자로 저장하고, 형식은 클라이언트 값이 아니라 확장자로 정한다
        verify(imageStorage).upload(endsWith(".jpg"), any(), anyLong(), eq("image/jpeg"));

        Study saved = em.find(Study.class, longAt(body, "$.data.studyId"));
        assertThat(saved.getInputType()).isEqualTo(InputType.IMAGE);
        assertThat(saved.getTextContent()).isNull();
        assertThat(saved.getExtractedText()).contains("5장");

        List<Integer> orders = em.createQuery(
                        "select i.sortOrder from StudyImage i where i.study.id = :id order by i.sortOrder", Integer.class)
                .setParameter("id", saved.getId())
                .getResultList();
        assertThat(orders).containsExactly(1, 2, 3, 4, 5);
    }

    // S1 입력 검사

    @Test
    void 입력이_없으면_STUDY_INPUT_EMPTY() throws Exception {
        expectError(study(text("   ")), 400, "STUDY_INPUT_EMPTY");
        expectError(study(multipartRequest().param("inputType", "TEXT")), 400, "STUDY_INPUT_EMPTY");
        expectError(study(multipartRequest().param("inputType", "IMAGE")), 400, "STUDY_INPUT_EMPTY");
    }

    @Test
    void 텍스트_길이_검사() throws Exception {
        expectError(study(text("가".repeat(19))), 422, "CONTENT_TOO_SHORT");
        study(text("가".repeat(20))).andExpect(status().isCreated());
        em.flush();
        em.clear();
        study(text("가".repeat(5000))).andExpect(status().isCreated());
        expectError(study(text("가".repeat(5001))), 400, "INVALID_REQUEST");
    }

    @Test
    void 메모는_200자까지() throws Exception {
        expectError(study(text(TEXT).param("memo", "가".repeat(201))), 400, "INVALID_REQUEST");
        study(text(TEXT).param("memo", "가".repeat(200))).andExpect(status().isCreated());
    }

    @Test
    void 입력_방식이_없거나_틀리면_INVALID_REQUEST() throws Exception {
        expectError(study(multipartRequest().param("content", TEXT)), 400, "INVALID_REQUEST");
        expectError(study(multipartRequest().param("inputType", "VIDEO").param("content", TEXT)), 400, "INVALID_REQUEST");
    }

    @Test
    void 사진_형식_장수_빈_파일은_IMAGE_INVALID() throws Exception {
        expectError(study(images(image("a.gif"))), 400, "IMAGE_INVALID");
        expectError(study(images(image("a.svg"))), 400, "IMAGE_INVALID");
        expectError(study(images(image("noextension"))), 400, "IMAGE_INVALID");
        expectError(study(images(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[0]))), 400, "IMAGE_INVALID");
        expectError(study(images(image("1.jpg"), image("2.jpg"), image("3.jpg"), image("4.jpg"), image("5.jpg"), image("6.jpg"))),
                400, "IMAGE_INVALID");
        verify(imageStorage, never()).upload(anyString(), any(), anyLong(), anyString());
        verify(aiClient, never()).generateQuiz(any());
    }

    @Test
    void multipart가_아니면_415() throws Exception {
        expectError(auth(post("/api/v1/trees/" + treeId + "/studies").contentType(MediaType.APPLICATION_JSON).content("{}")),
                415, "INVALID_REQUEST");
    }

    // S1 나무

    @Test
    void 나무_에러() throws Exception {
        expectError(auth(multipart("/api/v1/trees/999999999/studies").param("inputType", "TEXT").param("content", TEXT)),
                404, "TREE_NOT_FOUND");
        expectError(mockMvc.perform(multipartRequest().param("inputType", "TEXT").param("content", TEXT)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)), 403, "FORBIDDEN");
        mockMvc.perform(multipartRequest().param("inputType", "TEXT").param("content", TEXT))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 잎이_24개면_AI를_부르지_않고_TREE_FULL() throws Exception {
        Tree tree = em.find(Tree.class, treeId);
        for (int position = 1; position <= 24; position++) {
            Study study = Study.builder().tree(tree).inputType(InputType.TEXT).textContent(TEXT).build();
            em.persist(study);
            em.persist(Leaf.builder().tree(tree).study(study).position(position).grade(LeafGrade.GREEN).firstCorrectCount(6).build());
        }
        em.flush();
        em.clear();

        expectError(study(images(image("a.jpg"))), 409, "TREE_FULL");
        verify(aiClient, never()).generateQuiz(any());
        verify(imageStorage, never()).upload(anyString(), any(), anyLong(), anyString());
    }

    // S1 AI 에러: 저장하지 않고 올린 사진도 지운다

    @ParameterizedTest
    @CsvSource({
            "[mock:unreadable], 422, NOTE_UNREADABLE",
            "[mock:short], 422, CONTENT_TOO_SHORT",
            "[mock:error], 502, AI_SERVER_ERROR",
            "[mock:timeout], 504, AI_TIMEOUT"
    })
    void AI_에러면_저장하지_않고_사진을_지운다(String memo, int status, String code) throws Exception {
        expectError(study(images(image("a.jpg"), image("b.png")).param("memo", memo)), status, code);

        verify(imageStorage, times(2)).delete(anyString());
        assertThat(countStudies()).isZero();
    }

    @Test
    void AI를_기다리는_사이_나무가_삭제되면_TREE_NOT_FOUND이고_사진을_지운다() throws Exception {
        doAnswer(invocation -> {
            em.createNativeQuery("delete from trees where id = ?").setParameter(1, treeId).executeUpdate();
            em.clear();
            return invocation.callRealMethod();
        }).when(aiClient).generateQuiz(any());

        expectError(study(images(image("a.jpg"))), 404, "TREE_NOT_FOUND");
        verify(imageStorage).delete(anyString());
    }

    // 풀던 세트 교체

    @Test
    void 새_공부를_입력하면_풀던_첫_풀이_세트와_그_공부_기록을_지운다() throws Exception {
        String first = study(text(TEXT)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();

        String second = study(text(TEXT)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();

        assertThat(em.find(QuizSet.class, longAt(first, "$.data.quizSetId"))).isNull();
        assertThat(em.find(Study.class, longAt(first, "$.data.studyId"))).isNull();
        assertThat(em.find(Question.class, longAt(first, "$.data.questions[0].questionId"))).isNull();
        assertThat(countStudies()).isEqualTo(1);
        auth(get("/api/v1/trees/" + treeId))
                .andExpect(jsonPath("$.data.pendingQuizSetId").value(longAt(second, "$.data.quizSetId")));
    }

    @Test
    void 풀던_재도전_세트는_세트만_지우고_꽃봉오리의_공부_기록은_남긴다() throws Exception {
        Tree tree = em.find(Tree.class, treeId);
        Study budStudy = Study.builder().tree(tree).inputType(InputType.TEXT).textContent(TEXT).build();
        em.persist(budStudy);
        em.persist(Leaf.builder().tree(tree).study(budStudy).position(1).grade(LeafGrade.BUD).firstCorrectCount(0).build());
        QuizSet retry = QuizSet.builder().study(budStudy).type(QuizSetType.RETRY).attemptNo(1).gradedCount(6).build();
        em.persist(retry);
        em.flush();
        em.clear();

        study(text(TEXT)).andExpect(status().isCreated());
        em.flush();
        em.clear();

        assertThat(em.find(QuizSet.class, retry.getId())).isNull();
        assertThat(em.find(Study.class, budStudy.getId())).isNotNull();
    }

    @Test
    void 다른_나무의_풀던_세트와_복습_세트는_건드리지_않는다() throws Exception {
        Tree tree = em.find(Tree.class, treeId);
        Study leafStudy = Study.builder().tree(tree).inputType(InputType.TEXT).textContent(TEXT).build();
        em.persist(leafStudy);
        Leaf leaf = Leaf.builder().tree(tree).study(leafStudy).position(1).grade(LeafGrade.AUTUMN).firstCorrectCount(2).build();
        em.persist(leaf);
        QuizSet review = QuizSet.builder().study(leafStudy).leaf(leaf).type(QuizSetType.REVIEW).attemptNo(1).gradedCount(3).build();
        em.persist(review);
        Long otherTreeId = createTree();
        String other = study(multipart("/api/v1/trees/" + otherTreeId + "/studies").param("inputType", "TEXT").param("content", TEXT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        em.flush();
        em.clear();

        study(text(TEXT)).andExpect(status().isCreated());
        em.flush();
        em.clear();

        assertThat(em.find(QuizSet.class, review.getId())).isNotNull();
        assertThat(em.find(QuizSet.class, longAt(other, "$.data.quizSetId"))).isNotNull();
    }

    // S4 문제 다시 받기

    @Test
    void 제출_전_문제를_같은_내용으로_다시_준다() throws Exception {
        String created = study(text(TEXT)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        em.flush();
        em.clear();

        auth(get("/api/v1/quiz-sets/" + quizSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizSetId").value(quizSetId))
                .andExpect(jsonPath("$.data.quizSetType").value("INITIAL"))
                .andExpect(jsonPath("$.data.studyId").value(longAt(created, "$.data.studyId")))
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.subjectName").value("수학"))
                .andExpect(jsonPath("$.data.studyLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.questions[0].questionId").value(longAt(created, "$.data.questions[0].questionId")))
                .andExpect(jsonPath("$.data.questions[0].options[0]").value((String) JsonPath.read(created, "$.data.questions[0].options[0]")))
                .andExpect(jsonPath("$.data.questions[0].number").value(1))
                .andExpect(jsonPath("$.data.questions[6].number").value(7))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation").doesNotExist())
                .andExpect(jsonPath("$.data.questions[6].modelAnswer").doesNotExist());
    }

    @Test
    void 문제_다시_받기_에러() throws Exception {
        String created = study(text(TEXT)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        em.flush();

        expectError(auth(get("/api/v1/quiz-sets/999999999")), 404, "QUIZ_SET_NOT_FOUND");
        expectError(mockMvc.perform(get("/api/v1/quiz-sets/" + quizSetId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)), 403, "FORBIDDEN");

        em.createNativeQuery("update quiz_sets set status = 'SUBMITTED' where id = ?").setParameter(1, quizSetId).executeUpdate();
        em.clear();
        expectError(auth(get("/api/v1/quiz-sets/" + quizSetId)), 409, "ALREADY_SUBMITTED");
    }

    private long countStudies() {
        return em.createQuery("select count(s) from Study s where s.tree.id = :id", Long.class)
                .setParameter("id", treeId)
                .getSingleResult();
    }

    private MockMultipartHttpServletRequestBuilder multipartRequest() {
        return multipart("/api/v1/trees/" + treeId + "/studies");
    }

    private MockMultipartHttpServletRequestBuilder text(String content) {
        return multipartRequest().param("inputType", "TEXT").param("content", content);
    }

    private MockMultipartHttpServletRequestBuilder images(MockMultipartFile... files) {
        MockMultipartHttpServletRequestBuilder request = multipartRequest().param("inputType", "IMAGE");
        for (MockMultipartFile file : files) {
            request.file(file);
        }
        return request;
    }

    private MockMultipartFile image(String filename) {
        return new MockMultipartFile("images", filename, "application/octet-stream", new byte[]{1, 2, 3});
    }

    private ResultActions study(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return auth(request);
    }

    private ResultActions auth(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    private Long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private Long createTree() throws Exception {
        String response = auth(post("/api/v1/trees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectName\":\"수학\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return longAt(response, "$.data.treeId");
    }

    private String signUp(String nickname) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"%s\",\"password\":\"1234\"}".formatted(nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }
}

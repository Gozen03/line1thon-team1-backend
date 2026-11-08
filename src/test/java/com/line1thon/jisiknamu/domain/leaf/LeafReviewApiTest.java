package com.line1thon.jisiknamu.domain.leaf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
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
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.ai.ReviewFeedbackRequest;
import com.line1thon.jisiknamu.global.ai.ReviewQuestionsRequest;
import com.line1thon.jisiknamu.global.util.KstTime;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

// L2·R0·R1·R2와 S2 복습 제출. 실제 Security 필터와 MySQL, Mock AI로 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LeafReviewApiTest {

    private static final String TEXT = "이차함수의 꼭짓점과 판별식을 공부했다. 스무 글자가 넘는 내용이다.";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private AiClient aiClient;

    private String token;
    private String otherToken;
    private Long treeId;

    @BeforeEach
    void 회원과_나무를_만든다() throws Exception {
        token = signUp("복습하는사람");
        otherToken = signUp("다른사람");
        treeId = createTree("수학", token);
    }

    // L2 잎 상세

    @Test
    void 잎_상세는_공부_내용과_문제와_내_답과_피드백을_준다() throws Exception {
        Long leafId = leaf(2);

        auth(get("/api/v1/leaves/" + leafId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leafId").value(leafId))
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.subjectName").value("수학"))
                .andExpect(jsonPath("$.data.position").value(1))
                .andExpect(jsonPath("$.data.grade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.originalGrade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.studiedAt", startsWith("20")))
                .andExpect(jsonPath("$.data.reviewed").value(false))
                .andExpect(jsonPath("$.data.reviewedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.study.inputType").value("TEXT"))
                .andExpect(jsonPath("$.data.study.imageUrls", empty()))
                .andExpect(jsonPath("$.data.study.content").value(TEXT))
                .andExpect(jsonPath("$.data.study.memo").value("메모"))
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.questions[*].number", contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.data.questions[0].options", hasSize(4)))
                .andExpect(jsonPath("$.data.questions[*].difficulty", contains("EASY", "EASY", "NORMAL", "NORMAL", "HARD", "HARD", "ESSAY")))
                .andExpect(jsonPath("$.data.questions[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.questions[2].isCorrect").value(false))
                .andExpect(jsonPath("$.data.questions[2].explanation").isNotEmpty())
                .andExpect(jsonPath("$.data.questions[6].essayText").value("서술형 답"))
                .andExpect(jsonPath("$.data.questions[6].modelAnswer").isNotEmpty())
                .andExpect(jsonPath("$.data.questions[6].isCorrect").value(nullValue()))
                .andExpect(jsonPath("$.data.feedback.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.feedback.strengths", hasSize(2)))
                .andExpect(jsonPath("$.data.feedback.weaknesses", hasSize(4)))
                .andExpect(jsonPath("$.data.feedback.essayFeedback").isNotEmpty())
                .andExpect(jsonPath("$.data.quizSets", hasSize(1)))
                .andExpect(jsonPath("$.data.quizSets[0].quizSetType").value("INITIAL"))
                .andExpect(jsonPath("$.data.quizSets[0].correctCount").value(2));
    }

    @Test
    void 사진_공부는_사진_주소를_순서대로_주고_내용은_null이다() throws Exception {
        String created = auth(multipart("/api/v1/trees/" + treeId + "/studies")
                .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                .file(new MockMultipartFile("images", "b.png", "image/png", new byte[]{1}))
                .param("inputType", "IMAGE"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clearContext();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        Long leafId = longAt(submit(quizSetId, answers(quizSetId, 6, "답")).andReturn().getResponse().getContentAsString(), "$.data.leaf.leafId");
        clearContext();

        String body = auth(get("/api/v1/leaves/" + leafId))
                .andExpect(jsonPath("$.data.study.inputType").value("IMAGE"))
                .andExpect(jsonPath("$.data.study.content").value(nullValue()))
                .andExpect(jsonPath("$.data.study.imageUrls", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        List<String> urls = JsonPath.read(body, "$.data.study.imageUrls");
        assertThat(urls.get(0)).endsWith(".jpg");
        assertThat(urls.get(1)).endsWith(".png");
    }

    @Test
    void 잎눈과_재도전한_잎은_마지막_풀이의_문제를_보여_주고_세트_기록이_쌓인다() throws Exception {
        Long leafId = leaf(0);
        auth(get("/api/v1/leaves/" + leafId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.grade").value("BUD"));

        Long studyId = em.find(Leaf.class, leafId).getStudy().getId();
        clearContext();
        Long retrySetId = longAt(auth(post("/api/v1/studies/" + studyId + "/retry")).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        String firstQuestion = questions(retrySetId).get(0).getContent();
        submit(retrySetId, answers(retrySetId, 3, "답")).andExpect(status().isOk());
        clearContext();

        auth(get("/api/v1/leaves/" + leafId))
                .andExpect(jsonPath("$.data.grade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.originalGrade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.correctCount").value(3))
                .andExpect(jsonPath("$.data.questions[0].content").value(firstQuestion))
                .andExpect(jsonPath("$.data.quizSets[*].quizSetType", contains("INITIAL", "RETRY")));
    }

    @Test
    void 잎_상세_에러() throws Exception {
        Long leafId = leaf(3);
        expectError(auth(get("/api/v1/leaves/999999999")), 404, "LEAF_NOT_FOUND");
        expectError(as(otherToken, get("/api/v1/leaves/" + leafId)), 403, "FORBIDDEN");
    }

    // R0·R1 복습 목록

    @Test
    void 복습할_잎이_없으면_빈_목록이다() throws Exception {
        auth(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.nextReviewAt").value(nullValue()))
                .andExpect(jsonPath("$.data.reviews", empty()));
    }

    @Test
    void 전체_복습_목록은_지금_복습할_잎만_오래된_공부부터_주고_다음_시각을_알려_준다() throws Exception {
        Long newer = leaf(2);
        Long older = leaf(1);
        Long notYet = leaf(3);
        leaf(6); // 싱싱한 잎은 복습 대상이 아니다
        Long otherTree = createTree("물리", token);
        // 공부 순서를 바꿔 오래된 공부부터 나오는지 본다
        LocalDateTime now = KstTime.now();
        setStudiedAt(older, now.minusDays(9));
        setStudiedAt(newer, now.minusDays(5));
        makeAvailable(newer);
        makeAvailable(older);
        // 다른 회원의 잎은 나오지 않는다
        Long othersTree = createTree("남의 나무", otherToken);
        Long othersLeaf = leafOf(othersTree, otherToken, 2);
        makeAvailable(othersLeaf);
        clearContext();
        LocalDateTime nextDue = em.find(Leaf.class, notYet).getReviewDueAt();
        clearContext();

        String body = auth(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(2))
                .andExpect(jsonPath("$.data.reviews[*].leafId", contains(older.intValue(), newer.intValue())))
                .andExpect(jsonPath("$.data.reviews[0].treeId").value(treeId))
                .andExpect(jsonPath("$.data.reviews[0].subjectName").value("수학"))
                .andExpect(jsonPath("$.data.reviews[0].grade").value("BROWN"))
                .andExpect(jsonPath("$.data.reviews[0].weakTopics", hasSize(5)))
                .andExpect(jsonPath("$.data.reviews[0].questionCount").value(3))
                .andReturn().getResponse().getContentAsString();
        String nextReviewAt = JsonPath.read(body, "$.data.nextReviewAt");
        assertThat(OffsetDateTime.parse(nextReviewAt)).isEqualTo(KstTime.toOffset(nextDue));

        auth(get("/api/v1/trees/" + treeId + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.subjectName").value("수학"))
                .andExpect(jsonPath("$.data.reviews", hasSize(2)))
                .andExpect(jsonPath("$.data.reviews[0].leafId").value(older))
                .andExpect(jsonPath("$.data.reviews[0].treeId").doesNotExist())
                .andExpect(jsonPath("$.data.reviews[0].subjectName").doesNotExist());
        auth(get("/api/v1/trees/" + otherTree + "/reviews")).andExpect(jsonPath("$.data.reviews", empty()));
    }

    @Test
    void 나무별_목록_에러() throws Exception {
        expectError(auth(get("/api/v1/trees/999999999/reviews")), 404, "TREE_NOT_FOUND");
        expectError(as(otherToken, get("/api/v1/trees/" + treeId + "/reviews")), 403, "FORBIDDEN");
    }

    // R2 복습 출제

    @Test
    void 복습_출제는_객관식_3문제를_정답_없이_주고_AI에_틀린_문제를_넘긴다() throws Exception {
        Long leafId = leaf(2);
        makeAvailable(leafId);
        clearContext();

        String body = review(leafId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quizSetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.leafId").value(leafId))
                .andExpect(jsonPath("$.data.questions", hasSize(3)))
                .andExpect(jsonPath("$.data.questions[*].questionType", contains("MULTIPLE_CHOICE", "MULTIPLE_CHOICE", "MULTIPLE_CHOICE")))
                .andExpect(jsonPath("$.data.questions[0].options", hasSize(4)))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").doesNotExist())
                .andExpect(jsonPath("$.data.questions[0].explanation").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        clearContext();

        ArgumentCaptor<ReviewQuestionsRequest> captor = ArgumentCaptor.forClass(ReviewQuestionsRequest.class);
        verify(aiClient).generateReviewQuestions(captor.capture());
        ReviewQuestionsRequest request = captor.getValue();
        assertThat(request.subjectName()).isEqualTo("수학");
        assertThat(request.studyLevel()).isEqualTo(StudyLevel.HIGH);
        assertThat(request.content()).isEqualTo(TEXT);
        assertThat(request.memo()).isEqualTo("메모");
        assertThat(request.weakTopics()).hasSize(4);
        assertThat(request.wrongQuestions()).hasSize(4);
        assertThat(request.wrongQuestions().get(0).options()).hasSize(4);
        assertThat(request.wrongQuestions().get(0).selectedIndex()).isNotEqualTo(request.wrongQuestions().get(0).answerIndex());
        assertThat(request.excludeQuestions()).isEmpty();
        assertThat(request.count()).isEqualTo(3);

        QuizSet quizSet = em.find(QuizSet.class, longAt(body, "$.data.quizSetId"));
        assertThat(quizSet.getGradedCount()).isEqualTo(3);
        assertThat(quizSet.getLeaf().getId()).isEqualTo(leafId);
        assertThat(quizSet.getAttemptNo()).isEqualTo(1);

        // 복습 세트는 나무의 풀던 문제가 아니고, S4로는 다시 받을 수 있다
        auth(get("/api/v1/trees/" + treeId)).andExpect(jsonPath("$.data.pendingQuizSetId").value(nullValue()));
        auth(get("/api/v1/quiz-sets/" + quizSet.getId())).andExpect(jsonPath("$.data.quizSetType").value("REVIEW"));
    }

    @Test
    void 풀던_복습_세트가_있으면_AI를_부르지_않고_그_세트를_다시_준다() throws Exception {
        Long leafId = leaf(2);
        makeAvailable(leafId);
        clearContext();
        Long first = longAt(review(leafId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        clearInvocations(aiClient);

        review(leafId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.quizSetId").value(first))
                .andExpect(jsonPath("$.data.questions", hasSize(3)));
        verify(aiClient, never()).generateReviewQuestions(any());
    }

    @Test
    void 복습_출제_에러() throws Exception {
        expectError(review(999999999L), 404, "LEAF_NOT_FOUND");

        Long notYet = leaf(2);
        expectError(as(otherToken, post("/api/v1/leaves/" + notYet + "/reviews")), 403, "FORBIDDEN");
        expectError(review(notYet), 409, "REVIEW_NOT_AVAILABLE");

        Long green = leaf(6);
        expectError(review(green), 409, "REVIEW_NOT_AVAILABLE");
        Long bud = leaf(0);
        expectError(review(bud), 409, "REVIEW_NOT_AVAILABLE");
        verify(aiClient, never()).generateReviewQuestions(any());
    }

    // S2 복습 제출

    @Test
    void 복습에_성공하면_싱싱한_잎이_되고_약한_주제가_모두_RECOVERED가_되고_총평이_바뀐다() throws Exception {
        Long leafId = leaf(2);
        String originalSummary = em.find(Leaf.class, leafId).getFeedbackSummary();
        makeAvailable(leafId);
        Long other = leaf(2);
        makeAvailable(other);
        clearContext();
        Long setId = longAt(review(leafId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();

        // 약한 주제는 4개이고 복습은 3문제다. 문제로 나오지 않은 주제도 RECOVERED가 된다
        submit(setId, answers(setId, 3, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizSetType").value("REVIEW"))
                .andExpect(jsonPath("$.data.correctCount").value(3))
                .andExpect(jsonPath("$.data.gradedCount").value(3))
                .andExpect(jsonPath("$.data.passed").value(true))
                .andExpect(jsonPath("$.data.leaf.leafId").value(leafId))
                .andExpect(jsonPath("$.data.leaf.beforeGrade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.leaf.grade").value("GREEN"))
                .andExpect(jsonPath("$.data.leaf.studiedAt", startsWith("20")))
                .andExpect(jsonPath("$.data.leaf.reviewAvailableAt").value(nullValue()))
                .andExpect(jsonPath("$.data.recoveredTopics", hasSize(4)))
                .andExpect(jsonPath("$.data.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.remainingReviewCount").value(1))
                .andExpect(jsonPath("$.data.results", hasSize(3)))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[0].answerIndex").isNumber())
                .andExpect(jsonPath("$.data.results[0].explanation").isNotEmpty());
        clearContext();

        ArgumentCaptor<ReviewFeedbackRequest> captor = ArgumentCaptor.forClass(ReviewFeedbackRequest.class);
        verify(aiClient).generateReviewFeedback(captor.capture());
        assertThat(captor.getValue().originalSummary()).isEqualTo(originalSummary);
        assertThat(captor.getValue().passed()).isTrue();
        assertThat(captor.getValue().correctCount()).isEqualTo(3);
        assertThat(captor.getValue().results()).hasSize(3);

        Leaf leaf = em.find(Leaf.class, leafId);
        assertThat(leaf.getLeafType()).isEqualTo(LeafGrade.GREEN);
        assertThat(leaf.getOriginalType()).isEqualTo(LeafGrade.AUTUMN);
        assertThat(leaf.getRecoveredAt()).isNotNull();
        assertThat(leaf.getReviewDueAt()).isNull();
        assertThat(leaf.getFeedbackSummary()).isNotEqualTo(originalSummary);
        assertThat(topicCount(leafId, "WEAK")).isZero();
        assertThat(topicCount(leafId, "RECOVERED")).isEqualTo(4);
        assertThat(em.find(QuizSet.class, setId).getStatus()).isEqualTo(QuizSetStatus.SUBMITTED);

        auth(get("/api/v1/leaves/" + leafId))
                .andExpect(jsonPath("$.data.grade").value("GREEN"))
                .andExpect(jsonPath("$.data.originalGrade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.reviewed").value(true))
                .andExpect(jsonPath("$.data.reviewedAt", startsWith("20")))
                .andExpect(jsonPath("$.data.feedback.weaknesses", hasSize(4)))
                .andExpect(jsonPath("$.data.quizSets[*].quizSetType", containsInAnyOrder("INITIAL", "REVIEW")));
        auth(get("/api/v1/trees/" + treeId))
                .andExpect(jsonPath("$.data.leaves[0].reviewed").value(true))
                .andExpect(jsonPath("$.data.leaves[0].reviewAvailable").value(false));
        expectError(review(leafId), 409, "REVIEW_NOT_AVAILABLE");
    }

    @Test
    void 복습에_실패하면_등급과_총평은_그대로이고_1일_뒤_새_문제로_다시_복습한다() throws Exception {
        Long leafId = leaf(1);
        String originalSummary = em.find(Leaf.class, leafId).getFeedbackSummary();
        makeAvailable(leafId);
        clearContext();
        Long setId = longAt(review(leafId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        List<String> previous = questions(setId).stream().map(Question::getContent).toList();
        LocalDateTime start = KstTime.now();

        submit(setId, answers(setId, 2, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passed").value(false))
                .andExpect(jsonPath("$.data.correctCount").value(2))
                .andExpect(jsonPath("$.data.leaf.beforeGrade").value("BROWN"))
                .andExpect(jsonPath("$.data.leaf.grade").value("BROWN"))
                .andExpect(jsonPath("$.data.leaf.reviewAvailableAt", startsWith("20")))
                .andExpect(jsonPath("$.data.recoveredTopics", empty()))
                .andExpect(jsonPath("$.data.summary").value("아직 조금 헷갈려요. 한 번 더 복습하면 잘할 수 있어요."))
                .andExpect(jsonPath("$.data.remainingReviewCount").value(0));
        clearContext();

        verify(aiClient, never()).generateReviewFeedback(any());
        Leaf leaf = em.find(Leaf.class, leafId);
        assertThat(leaf.getLeafType()).isEqualTo(LeafGrade.BROWN);
        assertThat(leaf.getFeedbackSummary()).isEqualTo(originalSummary);
        assertThat(leaf.getRecoveredAt()).isNull();
        assertThat(leaf.getReviewDueAt()).isBetween(start.plusDays(1), KstTime.now().plusDays(1));
        assertThat(topicCount(leafId, "RECOVERED")).isZero();
        expectError(review(leafId), 409, "REVIEW_NOT_AVAILABLE");

        makeAvailable(leafId);
        clearContext();
        clearInvocations(aiClient);
        Long secondSet = longAt(review(leafId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();

        ArgumentCaptor<ReviewQuestionsRequest> captor = ArgumentCaptor.forClass(ReviewQuestionsRequest.class);
        verify(aiClient).generateReviewQuestions(captor.capture());
        assertThat(captor.getValue().excludeQuestions()).containsExactlyElementsOf(previous);
        assertThat(secondSet).isNotEqualTo(setId);
        assertThat(em.find(QuizSet.class, secondSet).getAttemptNo()).isEqualTo(2);
    }

    @Test
    void 복습_제출_에러() throws Exception {
        Long leafId = leaf(2);
        makeAvailable(leafId);
        clearContext();
        Long setId = longAt(review(leafId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        List<Question> questions = questions(setId);

        List<Object> two = new ArrayList<>();
        two.add(Map.of("questionId", questions.get(0).getId(), "selectedIndex", 0));
        two.add(Map.of("questionId", questions.get(1).getId(), "selectedIndex", 0));
        expectError(submit(setId, objectMapper.writeValueAsString(Map.of("answers", two))), 400, "ANSWER_COUNT_MISMATCH");

        submit(setId, answers(setId, 3, null)).andExpect(status().isOk());
        clearContext();
        expectError(submit(setId, answers(setId, 3, null)), 409, "ALREADY_SUBMITTED");
        expectError(auth(get("/api/v1/quiz-sets/" + setId)), 409, "ALREADY_SUBMITTED");
    }

    private Long leaf(int correct) throws Exception {
        return leafOf(treeId, token, correct);
    }

    private Long leafOf(Long targetTreeId, String ownerToken, int correct) throws Exception {
        String created = as(ownerToken, multipart("/api/v1/trees/" + targetTreeId + "/studies")
                .param("inputType", "TEXT").param("content", TEXT).param("memo", "메모"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clearContext();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        String submitted = as(ownerToken, post("/api/v1/quiz-sets/" + quizSetId + "/answers")
                .contentType(MediaType.APPLICATION_JSON).content(answers(quizSetId, correct, "서술형 답")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        clearContext();
        return longAt(submitted, "$.data.leaf.leafId");
    }

    private void makeAvailable(Long leafId) {
        em.flush();
        em.createNativeQuery("update leaves set review_due_at = ? where id = ?")
                .setParameter(1, KstTime.now().minusMinutes(1))
                .setParameter(2, leafId)
                .executeUpdate();
    }

    private void setStudiedAt(Long leafId, LocalDateTime studiedAt) {
        em.flush();
        em.createNativeQuery("update studies set created_at = ? where id = (select study_id from leaves where id = ?)")
                .setParameter(1, studiedAt)
                .setParameter(2, leafId)
                .executeUpdate();
    }

    private ResultActions review(Long leafId) throws Exception {
        return auth(post("/api/v1/leaves/" + leafId + "/reviews"));
    }

    private ResultActions submit(Long quizSetId, String body) throws Exception {
        return auth(post("/api/v1/quiz-sets/" + quizSetId + "/answers")
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    // 앞에서부터 correct개를 맞히고 나머지는 틀리는 답을 만든다
    private String answers(Long quizSetId, int correct, String essayText) {
        List<Object> items = new ArrayList<>();
        int answered = 0;
        for (Question question : questions(quizSetId)) {
            if (question.getAnswerIndex() == null) {
                items.add(Map.of("questionId", question.getId(), "essayText", essayText));
                continue;
            }
            int selected = answered < correct ? question.getAnswerIndex() : (question.getAnswerIndex() + 1) % 4;
            items.add(Map.of("questionId", question.getId(), "selectedIndex", selected));
            answered++;
        }
        return objectMapper.writeValueAsString(Map.of("answers", items));
    }

    private List<Question> questions(Long quizSetId) {
        return em.createQuery("select q from Question q where q.quizSet.id = :id order by q.number", Question.class)
                .setParameter("id", quizSetId)
                .getResultList();
    }

    private long topicCount(Long leafId, String status) {
        return ((Number) em.createNativeQuery("select count(*) from leaf_topics where leaf_id = ? and status = ?")
                .setParameter(1, leafId)
                .setParameter(2, status)
                .getSingleResult()).longValue();
    }

    private void clearContext() {
        em.flush();
        em.clear();
    }

    private ResultActions auth(AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return as(token, request);
    }

    private ResultActions as(String bearerToken, AbstractMockHttpServletRequestBuilder<?> request) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value(code));
    }

    private Long longAt(String json, String path) {
        return ((Number) JsonPath.read(json, path)).longValue();
    }

    private Long createTree(String subjectName, String ownerToken) throws Exception {
        String response = as(ownerToken, post("/api/v1/trees").contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectName\":\"%s\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}".formatted(subjectName)))
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

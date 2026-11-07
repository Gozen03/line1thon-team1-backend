package com.line1thon.jisiknamu.domain.tree;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.global.util.KstTime;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

// 실제 Security 필터와 MySQL까지 거쳐 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TreeApiTest {

    private static final String TREES = "/api/v1/trees";
    private static final String OFFSET_TIME = "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\+09:00";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    private String token;
    private String otherToken;

    @BeforeEach
    void 회원_두_명을_만든다() throws Exception {
        token = signUp("나무주인");
        otherToken = signUp("다른사람");
    }

    // T1 숲 조회

    @Test
    void 나무가_없으면_빈_숲() throws Exception {
        auth(get(TREES), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.freeTreeLimit").value(3))
                .andExpect(jsonPath("$.data.treeCount").value(0))
                .andExpect(jsonPath("$.data.canAddFreeTree").value(true))
                .andExpect(jsonPath("$.data.trees", hasSize(0)));
    }

    @Test
    void 숲은_만든_순서로_잎_수와_복습_수와_마지막_공부를_준다() throws Exception {
        Long first = createTree("수학");
        createTree("물리");
        createTree("다른사람_나무", otherToken);
        Tree tree = em.find(Tree.class, first);
        LocalDateTime past = KstTime.now().minusDays(1);
        addLeaf(tree, 1, LeafGrade.AUTUMN, past);
        addLeaf(tree, 2, LeafGrade.BROWN, past);
        addLeaf(tree, 3, LeafGrade.AUTUMN, KstTime.now().plusDays(1)); // 아직 복습 시점 아님
        addLeaf(tree, 4, LeafGrade.GREEN, past); // 복습 대상 아님
        em.flush();
        em.clear();

        auth(get(TREES), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treeCount").value(2))
                .andExpect(jsonPath("$.data.canAddFreeTree").value(true))
                .andExpect(jsonPath("$.data.trees", hasSize(2)))
                .andExpect(jsonPath("$.data.trees[0].subjectName").value("수학"))
                .andExpect(jsonPath("$.data.trees[0].treeShape").value("BASIC"))
                .andExpect(jsonPath("$.data.trees[0].leafCount").value(4))
                .andExpect(jsonPath("$.data.trees[0].reviewPendingCount").value(2))
                .andExpect(jsonPath("$.data.trees[0].lastStudiedAt", matchesPattern(OFFSET_TIME)))
                .andExpect(jsonPath("$.data.trees[1].subjectName").value("물리"))
                .andExpect(jsonPath("$.data.trees[1].leafCount").value(0))
                .andExpect(jsonPath("$.data.trees[1].reviewPendingCount").value(0))
                .andExpect(jsonPath("$.data.trees[1].lastStudiedAt").value(nullValue()));
    }

    @Test
    void 토큰이_없으면_401() throws Exception {
        mockMvc.perform(get(TREES))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    // T2 나무 추가

    @Test
    void 나무를_추가하면_201과_초_단위_생성_시각을_준다() throws Exception {
        auth(json(post(TREES), body(" 물리 ", "HIGH", "BASIC")), token)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.treeId").isNumber())
                .andExpect(jsonPath("$.data.subjectName").value("물리"))
                .andExpect(jsonPath("$.data.studyLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.treeShape").value("BASIC"))
                .andExpect(jsonPath("$.data.leafCount").value(0))
                .andExpect(jsonPath("$.data.createdAt", matchesPattern(OFFSET_TIME)));
    }

    @Test
    void 과목_이름은_20자까지_된다() throws Exception {
        auth(json(post(TREES), body("가".repeat(20), "HIGH", "BASIC")), token).andExpect(status().isCreated());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"subjectName\":\"\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}",
            "{\"subjectName\":\"   \",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}",
            "{\"subjectName\":\"가나다라마바사아자차카타파하가나다라마바사\",\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}",
            "{\"studyLevel\":\"HIGH\",\"treeShape\":\"BASIC\"}",
            "{\"subjectName\":\"물리\",\"treeShape\":\"BASIC\"}",
            "{\"subjectName\":\"물리\",\"studyLevel\":\"HIGH\"}",
            "{\"subjectName\":\"물리\",\"studyLevel\":\"PHD\",\"treeShape\":\"BASIC\"}",
            "{\"subjectName\":\"물리\",\"studyLevel\":\"HIGH\",\"treeShape\":\"OAK\"}"
    })
    void 요청_값이_틀리면_INVALID_REQUEST(String requestBody) throws Exception {
        expectError(auth(json(post(TREES), requestBody), token), 400, "INVALID_REQUEST");
    }

    @ParameterizedTest
    @ValueSource(strings = {"GINKGO", "MAPLE"})
    void 열리지_않은_모양은_TREE_SHAPE_LOCKED(String shape) throws Exception {
        expectError(auth(json(post(TREES), body("물리", "HIGH", shape)), token), 403, "TREE_SHAPE_LOCKED");
    }

    @Test
    void 네_번째_나무는_막히고_하나를_지우면_자리가_돌아온다() throws Exception {
        Long first = createTree("수학");
        createTree("물리");
        createTree("화학");

        auth(get(TREES), token)
                .andExpect(jsonPath("$.data.treeCount").value(3))
                .andExpect(jsonPath("$.data.canAddFreeTree").value(false));
        expectError(auth(json(post(TREES), body("생물", "HIGH", "BASIC")), token), 403, "TREE_LIMIT_EXCEEDED");

        auth(delete(TREES + "/" + first), token).andExpect(status().isNoContent());
        auth(json(post(TREES), body("생물", "HIGH", "BASIC")), token).andExpect(status().isCreated());
    }

    // T3 나무 상세

    @Test
    void 나무_상세는_잎을_자리_순서로_준다() throws Exception {
        Long treeId = createTree("수학");
        Tree tree = em.find(Tree.class, treeId);
        Leaf recovered = addLeaf(tree, 2, LeafGrade.AUTUMN, null);
        addLeaf(tree, 1, LeafGrade.BROWN, KstTime.now().minusDays(1));
        addLeaf(tree, 3, LeafGrade.AUTUMN, KstTime.now().plusDays(1));
        em.flush();
        em.createNativeQuery("update leaves set leaf_type = 'GREEN', recovered_at = now() where id = ?")
                .setParameter(1, recovered.getId())
                .executeUpdate();
        em.clear();

        auth(get(TREES + "/" + treeId), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.leafCount").value(3))
                .andExpect(jsonPath("$.data.reviewPendingCount").value(1))
                .andExpect(jsonPath("$.data.pendingQuizSetId").value(nullValue()))
                .andExpect(jsonPath("$.data.leaves[0].position").value(1))
                .andExpect(jsonPath("$.data.leaves[0].grade").value("BROWN"))
                .andExpect(jsonPath("$.data.leaves[0].reviewed").value(false))
                .andExpect(jsonPath("$.data.leaves[0].reviewAvailable").value(true))
                .andExpect(jsonPath("$.data.leaves[0].studiedAt", matchesPattern(OFFSET_TIME)))
                .andExpect(jsonPath("$.data.leaves[1].position").value(2))
                .andExpect(jsonPath("$.data.leaves[1].leafId").value(recovered.getId()))
                .andExpect(jsonPath("$.data.leaves[1].grade").value("GREEN"))
                .andExpect(jsonPath("$.data.leaves[1].originalGrade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.leaves[1].reviewed").value(true))
                .andExpect(jsonPath("$.data.leaves[1].reviewAvailable").value(false))
                .andExpect(jsonPath("$.data.leaves[2].position").value(3))
                .andExpect(jsonPath("$.data.leaves[2].reviewAvailable").value(false));
    }

    @Test
    void 풀던_문제는_제출_안_한_첫_풀이와_재도전만_알려_준다() throws Exception {
        Long treeId = createTree("수학");
        Tree tree = em.find(Tree.class, treeId);

        // 복습 세트는 잎에서 이어 풀므로 풀던 문제가 아니다
        Leaf leaf = addLeaf(tree, 1, LeafGrade.AUTUMN, null);
        persistQuizSet(leaf.getStudy(), leaf, QuizSetType.REVIEW, 3);
        // 제출한 세트도 풀던 문제가 아니다
        QuizSet submitted = persistQuizSet(leaf.getStudy(), null, QuizSetType.INITIAL, 6);
        em.flush();
        em.createNativeQuery("update quiz_sets set status = 'SUBMITTED' where id = ?")
                .setParameter(1, submitted.getId())
                .executeUpdate();
        em.clear();
        auth(get(TREES + "/" + treeId), token)
                .andExpect(jsonPath("$.data.pendingQuizSetId").value(nullValue()));

        Study study = persistStudy(em.find(Tree.class, treeId));
        QuizSet pending = persistQuizSet(study, null, QuizSetType.INITIAL, 6);
        em.flush();
        em.clear();
        auth(get(TREES + "/" + treeId), token)
                .andExpect(jsonPath("$.data.pendingQuizSetId").value(pending.getId()));
    }

    @Test
    void 나무_상세_에러() throws Exception {
        Long treeId = createTree("수학");
        expectError(auth(get(TREES + "/999999999"), token), 404, "TREE_NOT_FOUND");
        expectError(auth(get(TREES + "/" + treeId), otherToken), 403, "FORBIDDEN");
        expectError(auth(get(TREES + "/abc"), token), 400, "INVALID_REQUEST");
    }

    // T4 나무 수정

    @Test
    void 나무_수정은_보낸_필드만_바꾼다() throws Exception {
        Long treeId = createTree("수학");

        auth(json(patch(TREES + "/" + treeId), "{\"subjectName\":\" 고등 수학 \"}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.subjectName").value("고등 수학"))
                .andExpect(jsonPath("$.data.studyLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.treeShape").value("BASIC"));

        auth(json(patch(TREES + "/" + treeId), "{\"studyLevel\":\"UNIVERSITY\"}"), token)
                .andExpect(jsonPath("$.data.subjectName").value("고등 수학"))
                .andExpect(jsonPath("$.data.studyLevel").value("UNIVERSITY"));

        auth(json(patch(TREES + "/" + treeId), "{}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subjectName").value("고등 수학"));
    }

    @Test
    void 나무_모양은_수정으로_바뀌지_않는다() throws Exception {
        Long treeId = createTree("수학");
        auth(json(patch(TREES + "/" + treeId), "{\"treeShape\":\"MAPLE\"}"), token)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.treeShape").value("BASIC"));
    }

    @Test
    void 나무를_수정해도_이미_저장한_공부_기록의_값은_그대로다() throws Exception {
        Long treeId = createTree("수학");
        Study study = persistStudy(em.find(Tree.class, treeId));
        em.flush();

        auth(json(patch(TREES + "/" + treeId), "{\"subjectName\":\"고등 수학\",\"studyLevel\":\"UNIVERSITY\"}"), token)
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        Study saved = em.find(Study.class, study.getId());
        assertThat(saved.getSubjectName()).isEqualTo("수학");
        assertThat(saved.getStudyLevel()).isEqualTo(StudyLevel.HIGH);
        assertThat(saved.getTree().getSubjectName()).isEqualTo("고등 수학");
    }

    @Test
    void 나무_수정_에러() throws Exception {
        Long treeId = createTree("수학");
        expectError(auth(json(patch(TREES + "/" + treeId), "{\"subjectName\":\"  \"}"), token), 400, "INVALID_REQUEST");
        expectError(auth(json(patch(TREES + "/" + treeId), "{\"studyLevel\":\"PHD\"}"), token), 400, "INVALID_REQUEST");
        expectError(auth(json(patch(TREES + "/999999999"), "{}"), token), 404, "TREE_NOT_FOUND");
        expectError(auth(json(patch(TREES + "/" + treeId), "{}"), otherToken), 403, "FORBIDDEN");
    }

    // T5 나무 삭제

    @Test
    void 나무를_지우면_기록이_함께_지워지고_회원은_남는다() throws Exception {
        Long treeId = createTree("수학");
        Tree tree = em.find(Tree.class, treeId);
        Leaf leaf = addLeaf(tree, 1, LeafGrade.AUTUMN, null);
        Long studyId = leaf.getStudy().getId();
        Long leafId = leaf.getId();
        em.flush();
        em.clear(); // 실제 요청처럼 기록이 메모리에 없는 상태에서 지운다

        auth(delete(TREES + "/" + treeId), token)
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
        em.flush();
        em.clear();

        assertThat(em.find(Tree.class, treeId)).isNull();
        assertThat(em.find(Leaf.class, leafId)).isNull();
        assertThat(em.find(Study.class, studyId)).isNull();
        auth(get("/api/v1/users/me"), token).andExpect(status().isOk());
    }

    @Test
    void 나무_삭제_에러() throws Exception {
        Long treeId = createTree("수학");
        expectError(auth(delete(TREES + "/999999999"), token), 404, "TREE_NOT_FOUND");
        expectError(auth(delete(TREES + "/" + treeId), otherToken), 403, "FORBIDDEN");
        auth(get(TREES + "/" + treeId), token).andExpect(status().isOk());
    }

    private Leaf addLeaf(Tree tree, int position, LeafGrade grade, LocalDateTime reviewDueAt) {
        Leaf leaf = Leaf.builder().tree(tree).study(persistStudy(tree)).position(position).grade(grade)
                .firstCorrectCount(2).reviewDueAt(reviewDueAt).build();
        em.persist(leaf);
        return leaf;
    }

    private Study persistStudy(Tree tree) {
        Study study = Study.builder().tree(tree).inputType(InputType.TEXT).textContent("공부 내용").build();
        em.persist(study);
        return study;
    }

    private QuizSet persistQuizSet(Study study, Leaf leaf, QuizSetType type, int gradedCount) {
        QuizSet quizSet = QuizSet.builder().study(study).leaf(leaf).type(type).attemptNo(1).gradedCount(gradedCount).build();
        em.persist(quizSet);
        return quizSet;
    }

    private Long createTree(String subjectName) throws Exception {
        return createTree(subjectName, token);
    }

    private Long createTree(String subjectName, String ownerToken) throws Exception {
        String response = auth(json(post(TREES), body(subjectName, "HIGH", "BASIC")), ownerToken)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.treeId")).longValue();
    }

    private String signUp(String nickname) throws Exception {
        String response = mockMvc.perform(post("/api/v1/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nickname\":\"%s\",\"password\":\"1234\"}".formatted(nickname)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.accessToken");
    }

    private ResultActions auth(MockHttpServletRequestBuilder request, String bearerToken) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String requestBody) {
        return request.contentType(MediaType.APPLICATION_JSON).content(requestBody);
    }

    private String body(String subjectName, String studyLevel, String treeShape) {
        return "{\"subjectName\":\"%s\",\"studyLevel\":\"%s\",\"treeShape\":\"%s\"}".formatted(subjectName, studyLevel, treeShape);
    }

    private void expectError(ResultActions result, int status, String code) throws Exception {
        result.andExpect(status().is(status))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value(code));
    }
}

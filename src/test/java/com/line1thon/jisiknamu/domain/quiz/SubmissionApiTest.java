package com.line1thon.jisiknamu.domain.quiz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.ai.FeedbackRequest;
import com.line1thon.jisiknamu.global.ai.QuizGenerateRequest;
import com.line1thon.jisiknamu.global.util.KstTime;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

// S2·S3. 실제 Security 필터와 MySQL, Mock AI로 확인한다. 트랜잭션은 테스트 뒤 롤백된다
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SubmissionApiTest {

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
        token = signUp("채점받는사람");
        otherToken = signUp("다른사람");
        treeId = createTree();
    }

    // 첫 풀이: 등급

    @ParameterizedTest
    @CsvSource({
            "6, GREEN, 완벽하게 이해했어요, false",
            "5, LIGHT_GREEN, 잘 이해했어요, false",
            "4, LIGHT_GREEN, 잘 이해했어요, false",
            "3, AUTUMN, 조금 더 다지면 싱싱한 잎이 돼요, true",
            "2, AUTUMN, 조금 더 다지면 싱싱한 잎이 돼요, true",
            "1, BROWN, 다시 공부가 필요해요, true",
            "0, BUD, 기록은 남기고 재도전, false"
    })
    void 맞힌_개수로_등급을_정하고_잎이_생긴다(int correct, String grade, String message, boolean reviewTarget) throws Exception {
        Long quizSetId = newStudy();

        submit(quizSetId, answers(quizSetId, correct, "서술형 답"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizSetId").value(quizSetId))
                .andExpect(jsonPath("$.data.quizSetType").value("INITIAL"))
                .andExpect(jsonPath("$.data.correctCount").value(correct))
                .andExpect(jsonPath("$.data.gradedCount").value(6))
                .andExpect(jsonPath("$.data.leaf.leafId").isNumber())
                .andExpect(jsonPath("$.data.leaf.treeId").value(treeId))
                .andExpect(jsonPath("$.data.leaf.position").value(1))
                .andExpect(jsonPath("$.data.leaf.grade").value(grade))
                .andExpect(jsonPath("$.data.leaf.message").value(message))
                .andExpect(jsonPath("$.data.leaf.reviewTarget").value(reviewTarget))
                .andExpect(jsonPath("$.data.leaf.treeLeafCount").value(1));

        Leaf leaf = leafOfTree();
        assertThat(leaf.getLeafType()).isEqualTo(LeafGrade.valueOf(grade));
        assertThat(leaf.getOriginalType()).isEqualTo(LeafGrade.valueOf(grade));
        assertThat(leaf.getFirstCorrectCount()).isEqualTo(correct);
        assertThat(leaf.getFeedbackSummary()).isNotBlank();
        assertThat(leaf.getReviewDueAt() != null).isEqualTo(reviewTarget);
    }

    @Test
    void 복습_대상은_3일_뒤_복습_가능하고_아니면_null이다() throws Exception {
        Long autumn = newStudy();
        LocalDateTime before = KstTime.now();
        String body = submit(autumn, answers(autumn, 3, "답")).andReturn().getResponse().getContentAsString();

        String at = JsonPath.read(body, "$.data.leaf.reviewAvailableAt");
        assertThat(at).endsWith("+09:00");
        assertThat(leafOfTree().getReviewDueAt()).isBetween(before.plusDays(3), KstTime.now().plusDays(3));

        Long green = newStudy();
        submit(green, answers(green, 6, "답"))
                .andExpect(jsonPath("$.data.leaf.position").value(2))
                .andExpect(jsonPath("$.data.leaf.treeLeafCount").value(2))
                .andExpect(jsonPath("$.data.leaf.reviewAvailableAt").value(nullValue()));
    }

    // 첫 풀이: 결과와 저장

    @Test
    void 응답에_문제별_결과와_정답과_피드백을_준다() throws Exception {
        Long quizSetId = newStudy();

        submit(quizSetId, answers(quizSetId, 2, "그래프가 가장 낮아지는 점"))
                .andExpect(jsonPath("$.data.results", hasSize(7)))
                .andExpect(jsonPath("$.data.results[*].number", contains(1, 2, 3, 4, 5, 6, 7)))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(true))
                .andExpect(jsonPath("$.data.results[0].answerIndex").value(0))
                .andExpect(jsonPath("$.data.results[0].explanation").isNotEmpty())
                .andExpect(jsonPath("$.data.results[2].isCorrect").value(false))
                .andExpect(jsonPath("$.data.results[6].questionType").value("ESSAY"))
                .andExpect(jsonPath("$.data.results[6].isCorrect").value(nullValue()))
                .andExpect(jsonPath("$.data.results[6].essayText").value("그래프가 가장 낮아지는 점"))
                .andExpect(jsonPath("$.data.results[6].modelAnswer").isNotEmpty())
                .andExpect(jsonPath("$.data.feedback.summary").isNotEmpty())
                .andExpect(jsonPath("$.data.feedback.essayFeedback").isNotEmpty())
                .andExpect(jsonPath("$.data.feedback.strengths", hasSize(2)))
                .andExpect(jsonPath("$.data.feedback.weaknesses", hasSize(4)));
    }

    @Test
    void 안_푼_문제는_오답이고_서술형이_비면_고정_문구다() throws Exception {
        Long quizSetId = newStudy();
        List<Question> questions = questions(quizSetId);
        List<Object> items = new ArrayList<>();
        for (Question question : questions) {
            items.add(java.util.Collections.singletonMap("questionId", question.getId()));
        }

        submit(quizSetId, json(items))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(0))
                .andExpect(jsonPath("$.data.leaf.grade").value("BUD"))
                .andExpect(jsonPath("$.data.results[0].selectedIndex").value(nullValue()))
                .andExpect(jsonPath("$.data.results[0].isCorrect").value(false))
                .andExpect(jsonPath("$.data.feedback.essayFeedback").value("답을 적지 않았어요. 모범답안을 보고 핵심을 정리해 보세요."));
    }

    @Test
    void AI2에_채점_결과와_서술형_채점_포인트를_보낸다() throws Exception {
        Long quizSetId = newStudy();

        submit(quizSetId, answers(quizSetId, 3, "서술형 답")).andExpect(status().isOk());

        ArgumentCaptor<FeedbackRequest> captor = ArgumentCaptor.forClass(FeedbackRequest.class);
        verify(aiClient).generateFeedback(captor.capture());
        FeedbackRequest request = captor.getValue();
        assertThat(request.subjectName()).isEqualTo("수학");
        assertThat(request.studyLevel()).isEqualTo(StudyLevel.HIGH);
        assertThat(request.correctCount()).isEqualTo(3);
        assertThat(request.gradedCount()).isEqualTo(6);
        assertThat(request.grade()).isEqualTo(LeafGrade.AUTUMN);
        assertThat(request.results()).hasSize(6);
        assertThat(request.results()).extracting(FeedbackRequest.QuestionResult::isCorrect)
                .containsExactly(true, true, true, false, false, false);
        assertThat(request.essay().essayText()).isEqualTo("서술형 답");
        assertThat(request.essay().modelAnswer()).isNotBlank();
        assertThat(request.essay().gradingPoints()).hasSize(2);
    }

    @Test
    void 답과_주제와_세트_상태를_저장하고_풀던_문제에서_빠진다() throws Exception {
        Long quizSetId = newStudy();
        submit(quizSetId, answers(quizSetId, 2, "서술형 답")).andExpect(status().isOk());
        em.flush();
        em.clear();

        QuizSet quizSet = em.find(QuizSet.class, quizSetId);
        assertThat(quizSet.getStatus()).isEqualTo(QuizSetStatus.SUBMITTED);
        assertThat(quizSet.getCorrectCount()).isEqualTo(2);
        assertThat(quizSet.getSubmittedAt()).isNotNull();

        List<Answer> saved = em.createQuery(
                        "select a from Answer a join fetch a.question q where q.quizSet.id = :id order by q.number", Answer.class)
                .setParameter("id", quizSetId)
                .getResultList();
        assertThat(saved).hasSize(7);
        assertThat(saved.get(0).getCorrect()).isTrue();
        assertThat(saved.get(2).getCorrect()).isFalse();
        assertThat(saved.get(6).getCorrect()).isNull();
        assertThat(saved.get(6).getEssayText()).isEqualTo("서술형 답");
        assertThat(saved.get(6).getAiFeedback()).isNotBlank();

        assertThat(topicCount("STRONG")).isEqualTo(2);
        assertThat(topicCount("WEAK")).isEqualTo(4);

        auth(get("/api/v1/trees/" + treeId))
                .andExpect(jsonPath("$.data.pendingQuizSetId").value(nullValue()))
                .andExpect(jsonPath("$.data.leafCount").value(1))
                .andExpect(jsonPath("$.data.leaves[0].grade").value("AUTUMN"));
        expectError(auth(get("/api/v1/quiz-sets/" + quizSetId)), 409, "ALREADY_SUBMITTED");
    }

    // S2 에러

    @Test
    void 세트_에러() throws Exception {
        Long quizSetId = newStudy();
        String answers = answers(quizSetId, 3, "답");

        expectError(submit(999999999L, answers), 404, "QUIZ_SET_NOT_FOUND");
        expectError(mockMvc.perform(post("/api/v1/quiz-sets/" + quizSetId + "/answers")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(answers)), 403, "FORBIDDEN");
        verify(aiClient, never()).generateFeedback(any());

        submit(quizSetId, answers).andExpect(status().isOk());
        em.flush();
        em.clear();
        expectError(submit(quizSetId, answers), 409, "ALREADY_SUBMITTED");
    }

    @Test
    void 답_개수나_문제_번호가_다르면_ANSWER_COUNT_MISMATCH() throws Exception {
        Long quizSetId = newStudy();
        List<Question> questions = questions(quizSetId);

        expectError(submit(quizSetId, "{}"), 400, "ANSWER_COUNT_MISMATCH");
        expectError(submit(quizSetId, "{\"answers\":[]}"), 400, "ANSWER_COUNT_MISMATCH");

        List<Object> six = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            six.add(java.util.Map.of("questionId", questions.get(i).getId(), "selectedIndex", 0));
        }
        expectError(submit(quizSetId, json(six)), 400, "ANSWER_COUNT_MISMATCH");

        List<Object> duplicated = new ArrayList<>(six);
        duplicated.add(six.get(0));
        expectError(submit(quizSetId, json(duplicated)), 400, "ANSWER_COUNT_MISMATCH");

        List<Object> unknown = new ArrayList<>(six);
        unknown.add(java.util.Map.of("questionId", 999999999L, "selectedIndex", 0));
        expectError(submit(quizSetId, json(unknown)), 400, "ANSWER_COUNT_MISMATCH");
        verify(aiClient, never()).generateFeedback(any());
    }

    @Test
    void 답_값이_틀리면_INVALID_REQUEST() throws Exception {
        Long quizSetId = newStudy();
        List<Question> questions = questions(quizSetId);

        List<Object> outOfRange = new ArrayList<>();
        for (Question question : questions) {
            outOfRange.add(java.util.Map.of("questionId", question.getId(), "selectedIndex", 4));
        }
        expectError(submit(quizSetId, json(outOfRange)), 400, "INVALID_REQUEST");

        expectError(submit(quizSetId, answers(quizSetId, 1, "가".repeat(1001))), 400, "INVALID_REQUEST");
        submit(quizSetId, answers(quizSetId, 1, "가".repeat(1000))).andExpect(status().isOk());
    }

    @Test
    void AI_오류면_저장하지_않고_같은_답으로_다시_제출할_수_있다() throws Exception {
        Long quizSetId = newStudy();

        expectError(submit(quizSetId, answers(quizSetId, 3, "[mock:error]")), 502, "AI_SERVER_ERROR");
        expectError(submit(quizSetId, answers(quizSetId, 3, "[mock:timeout]")), 504, "AI_TIMEOUT");
        em.flush();
        em.clear();

        assertThat(em.find(QuizSet.class, quizSetId).getStatus()).isEqualTo(QuizSetStatus.CREATED);
        assertThat(leafCount()).isZero();

        submit(quizSetId, answers(quizSetId, 3, "답")).andExpect(status().isOk());
    }

    @Test
    void AI를_기다리는_사이_세트가_지워지면_QUIZ_SET_NOT_FOUND() throws Exception {
        Long quizSetId = newStudy();
        String answers = answers(quizSetId, 3, "답");
        Mockito.doAnswer(invocation -> {
            em.createNativeQuery("delete from quiz_sets where id = ?").setParameter(1, quizSetId).executeUpdate();
            em.clear();
            return invocation.callRealMethod();
        }).when(aiClient).generateFeedback(any());

        expectError(submit(quizSetId, answers), 404, "QUIZ_SET_NOT_FOUND");
        assertThat(leafCount()).isZero();
    }

    // S3 재도전

    @Test
    void 잎눈면_새_7문제를_받고_풀던_문제로_보인다() throws Exception {
        Long studyId = budStudy();

        String body = retry(studyId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.studyId").value(studyId))
                .andExpect(jsonPath("$.data.quizSetType").value("RETRY"))
                .andExpect(jsonPath("$.data.treeId").value(treeId))
                .andExpect(jsonPath("$.data.questions", hasSize(7)))
                .andExpect(jsonPath("$.data.questions[0].answerIndex").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        Long retrySetId = longAt(body, "$.data.quizSetId");
        em.flush();
        em.clear();

        assertThat(em.find(QuizSet.class, retrySetId).getAttemptNo()).isEqualTo(1);
        assertThat(em.find(QuizSet.class, retrySetId).getGradedCount()).isEqualTo(6);
        auth(get("/api/v1/trees/" + treeId)).andExpect(jsonPath("$.data.pendingQuizSetId").value(retrySetId));
        auth(get("/api/v1/quiz-sets/" + retrySetId)).andExpect(jsonPath("$.data.quizSetType").value("RETRY"));
    }

    @Test
    void AI1에_저장한_값과_약한_주제와_이전_문제를_넘긴다() throws Exception {
        Long studyId = budStudy();
        List<String> previous = em.createQuery(
                        "select q.content from Question q where q.quizSet.study.id = :id order by q.number", String.class)
                .setParameter("id", studyId)
                .getResultList();
        List<String> weak = em.createQuery(
                        "select t.topic from LeafTopic t where t.leaf.study.id = :id and t.status = com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus.WEAK",
                        String.class)
                .setParameter("id", studyId)
                .getResultList();
        // 나무를 고쳐도 공부할 때의 값으로 출제한다
        auth(patch("/api/v1/trees/" + treeId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subjectName\":\"고등 수학\",\"studyLevel\":\"UNIVERSITY\"}")).andExpect(status().isOk());
        em.flush();
        em.clear();
        Mockito.clearInvocations(aiClient);

        retry(studyId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.subjectName").value("수학"))
                .andExpect(jsonPath("$.data.studyLevel").value("HIGH"));

        ArgumentCaptor<QuizGenerateRequest> captor = ArgumentCaptor.forClass(QuizGenerateRequest.class);
        verify(aiClient).generateQuiz(captor.capture());
        QuizGenerateRequest request = captor.getValue();
        assertThat(request.quizSetType()).isEqualTo(QuizSetType.RETRY);
        assertThat(request.subjectName()).isEqualTo("수학");
        assertThat(request.studyLevel()).isEqualTo(StudyLevel.HIGH);
        assertThat(request.inputType()).isEqualTo(InputType.TEXT);
        assertThat(request.imageUrls()).isEmpty();
        assertThat(request.content()).isEqualTo(TEXT);
        assertThat(request.memo()).isEqualTo("메모");
        assertThat(request.weakTopics()).containsExactlyInAnyOrderElementsOf(weak);
        assertThat(request.excludeQuestions()).containsExactlyElementsOf(previous).hasSize(7);
    }

    @Test
    void 사진_공부의_재도전은_읽어_둔_내용을_TEXT로_보낸다() throws Exception {
        String created = auth(multipart("/api/v1/trees/" + treeId + "/studies")
                .file(new MockMultipartFile("images", "a.jpg", "image/jpeg", new byte[]{1}))
                .param("inputType", "IMAGE"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clearContext();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        Long studyId = longAt(created, "$.data.studyId");
        String extracted = em.find(Study.class, studyId).getExtractedText();
        submit(quizSetId, answers(quizSetId, 0, "답")).andExpect(status().isOk());
        clearContext();
        Mockito.clearInvocations(aiClient);

        retry(studyId).andExpect(status().isCreated());

        ArgumentCaptor<QuizGenerateRequest> captor = ArgumentCaptor.forClass(QuizGenerateRequest.class);
        verify(aiClient).generateQuiz(captor.capture());
        assertThat(captor.getValue().inputType()).isEqualTo(InputType.TEXT);
        assertThat(captor.getValue().content()).isEqualTo(extracted).isNotBlank();
        assertThat(captor.getValue().imageUrls()).isEmpty();
    }

    @Test
    void 다시_재도전하면_풀던_세트를_지우고_번호가_겹치지_않는다() throws Exception {
        Long studyId = budStudy();
        Long first = longAt(retry(studyId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();

        Long second = longAt(retry(studyId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();

        assertThat(em.find(QuizSet.class, first)).isNull();
        assertThat(em.find(QuizSet.class, second).getAttemptNo()).isEqualTo(1);

        // 제출한 재도전은 기록으로 남고 다음 번호를 쓴다
        submit(second, answers(second, 0, "답")).andExpect(status().isOk());
        clearContext();
        Long third = longAt(retry(studyId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        assertThat(em.find(QuizSet.class, second)).isNotNull();
        assertThat(em.find(QuizSet.class, third).getAttemptNo()).isEqualTo(2);
    }

    @Test
    void 재도전_요청_에러() throws Exception {
        expectError(retry(999999999L), 404, "STUDY_NOT_FOUND");

        Long studyId = budStudy();
        expectError(mockMvc.perform(post("/api/v1/studies/" + studyId + "/retry")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken)), 403, "FORBIDDEN");

        // 잎눈이 아닌 잎
        Long green = newStudy();
        Long greenStudyId = em.find(QuizSet.class, green).getStudy().getId();
        submit(green, answers(green, 6, "답")).andExpect(status().isOk());
        clearContext();
        expectError(retry(greenStudyId), 409, "NOT_BUD_RESULT");

        // 아직 잎이 없는 공부
        Long pending = newStudy();
        expectError(retry(em.find(QuizSet.class, pending).getStudy().getId()), 409, "NOT_BUD_RESULT");
    }

    // 재도전 제출

    @Test
    void 재도전을_제출하면_같은_잎의_등급과_주제가_바뀌고_이전_세트는_남는다() throws Exception {
        Long studyId = budStudy();
        Leaf before = leafOfTree();
        Long leafId = before.getId();
        int position = before.getPosition();
        Long initialSetId = em.createQuery("select q.id from QuizSet q where q.study.id = :id", Long.class)
                .setParameter("id", studyId).getSingleResult();
        Long retrySetId = longAt(retry(studyId).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();
        LocalDateTime start = KstTime.now();

        submit(retrySetId, answers(retrySetId, 3, "답"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quizSetType").value("RETRY"))
                .andExpect(jsonPath("$.data.leaf.leafId").value(leafId))
                .andExpect(jsonPath("$.data.leaf.position").value(position))
                .andExpect(jsonPath("$.data.leaf.grade").value("AUTUMN"))
                .andExpect(jsonPath("$.data.leaf.reviewTarget").value(true))
                .andExpect(jsonPath("$.data.leaf.treeLeafCount").value(1));
        clearContext();

        Leaf after = em.find(Leaf.class, leafId);
        assertThat(after.getLeafType()).isEqualTo(LeafGrade.AUTUMN);
        assertThat(after.getOriginalType()).isEqualTo(LeafGrade.AUTUMN);
        assertThat(after.getFirstCorrectCount()).isEqualTo(3);
        assertThat(after.getReviewDueAt()).isBetween(start.plusDays(3), KstTime.now().plusDays(3));
        assertThat(leafCount()).isEqualTo(1);
        assertThat(topicCount("STRONG")).isEqualTo(3);
        assertThat(topicCount("WEAK")).isGreaterThan(0).isLessThanOrEqualTo(3);
        assertThat(em.find(QuizSet.class, initialSetId).getStatus()).isEqualTo(QuizSetStatus.SUBMITTED);
    }

    @Test
    void 재도전이_다시_0개면_잎눈_그대로이고_또_재도전할_수_있다() throws Exception {
        Long studyId = budStudy();
        Long retrySetId = longAt(retry(studyId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        clearContext();

        submit(retrySetId, answers(retrySetId, 0, "답"))
                .andExpect(jsonPath("$.data.leaf.grade").value("BUD"))
                .andExpect(jsonPath("$.data.leaf.reviewTarget").value(false))
                .andExpect(jsonPath("$.data.leaf.reviewAvailableAt").value(nullValue()));
        clearContext();

        retry(studyId).andExpect(status().isCreated());
    }

    @Test
    void 재도전_제출_때_잎이_잎눈이_아니면_NOT_BUD_RESULT() throws Exception {
        Long studyId = budStudy();
        Long retrySetId = longAt(retry(studyId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        em.flush();
        em.createNativeQuery("update leaves set leaf_type = 'GREEN' where study_id = ?").setParameter(1, studyId).executeUpdate();
        em.clear();

        expectError(submit(retrySetId, answers(retrySetId, 3, "답")), 409, "NOT_BUD_RESULT");
    }

    @Test
    void 나무에_다른_공부를_입력하면_풀던_재도전_세트가_지워져_제출할_수_없다() throws Exception {
        Long studyId = budStudy();
        Long retrySetId = longAt(retry(studyId).andReturn().getResponse().getContentAsString(), "$.data.quizSetId");
        String retryAnswers = answers(retrySetId, 3, "답");
        clearContext();

        newStudy();

        expectError(submit(retrySetId, retryAnswers), 404, "QUIZ_SET_NOT_FOUND");
        assertThat(em.find(Study.class, studyId)).isNotNull();
    }

    private Long budStudy() throws Exception {
        String created = auth(multipart("/api/v1/trees/" + treeId + "/studies")
                .param("inputType", "TEXT").param("content", TEXT).param("memo", "메모"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clearContext();
        Long quizSetId = longAt(created, "$.data.quizSetId");
        submit(quizSetId, answers(quizSetId, 0, "답")).andExpect(status().isOk());
        clearContext();
        return longAt(created, "$.data.studyId");
    }

    private Long newStudy() throws Exception {
        String created = auth(multipart("/api/v1/trees/" + treeId + "/studies")
                .param("inputType", "TEXT").param("content", TEXT))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        clearContext();
        return longAt(created, "$.data.quizSetId");
    }

    private ResultActions retry(Long studyId) throws Exception {
        return auth(post("/api/v1/studies/" + studyId + "/retry"));
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
                items.add(java.util.Map.of("questionId", question.getId(), "essayText", essayText));
                continue;
            }
            int selected = answered < correct ? question.getAnswerIndex() : (question.getAnswerIndex() + 1) % 4;
            items.add(java.util.Map.of("questionId", question.getId(), "selectedIndex", selected));
            answered++;
        }
        return json(items);
    }

    private String json(List<Object> items) {
        return objectMapper.writeValueAsString(java.util.Map.of("answers", items));
    }

    private List<Question> questions(Long quizSetId) {
        return em.createQuery("select q from Question q where q.quizSet.id = :id order by q.number", Question.class)
                .setParameter("id", quizSetId)
                .getResultList();
    }

    private Leaf leafOfTree() {
        clearContext();
        return em.createQuery("select l from Leaf l where l.tree.id = :id order by l.position", Leaf.class)
                .setParameter("id", treeId)
                .setMaxResults(1)
                .getSingleResult();
    }

    private long leafCount() {
        return em.createQuery("select count(l) from Leaf l where l.tree.id = :id", Long.class)
                .setParameter("id", treeId)
                .getSingleResult();
    }

    private long topicCount(String status) {
        return ((Number) em.createNativeQuery(
                        "select count(*) from leaf_topics t join leaves l on t.leaf_id = l.id where l.tree_id = ? and t.status = ?")
                .setParameter(1, treeId)
                .setParameter(2, status)
                .getSingleResult()).longValue();
    }

    private void clearContext() {
        em.flush();
        em.clear();
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

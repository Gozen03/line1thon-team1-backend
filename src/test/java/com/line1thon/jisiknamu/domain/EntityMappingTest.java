package com.line1thon.jisiknamu.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.line1thon.jisiknamu.domain.ad.entity.AdCounter;
import com.line1thon.jisiknamu.domain.ad.entity.AdCounterId;
import com.line1thon.jisiknamu.domain.ad.entity.AdPlacement;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafTopic;
import com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.study.entity.StudyImage;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.entity.TreeShape;
import com.line1thon.jisiknamu.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

// 실제 MySQL 스키마에 저장·조회해 본다. 트랜잭션은 테스트 뒤 롤백된다. 로컬 Docker MySQL이 켜져 있어야 통과한다
@SpringBootTest
@Transactional
class EntityMappingTest {

    @Autowired
    private EntityManager em;

    private User user;
    private Tree tree;
    private Study study;
    private StudyImage image;
    private Leaf leaf;
    private LeafTopic topic;
    private QuizSet quizSet;
    private Question question;
    private Answer answer;
    private AdCounterId adCounterId;

    @BeforeEach
    void 전체_엔티티를_저장한다() {
        user = User.builder().nickname("매핑테스트").passwordHash("$2a$10$hash").build();
        em.persist(user);

        tree = Tree.builder().user(user).subjectName("수학").studyLevel(StudyLevel.SELF_STUDY).shape(TreeShape.BASIC).build();
        em.persist(tree);

        study = Study.builder().tree(tree).inputType(InputType.TEXT).textContent("미분 정리").memo("메모").build();
        em.persist(study);

        image = StudyImage.builder().study(study).s3Key("studies/1/a.jpg").sortOrder(1).build();
        em.persist(image);

        leaf = Leaf.builder().tree(tree).study(study).position(13).grade(LeafGrade.LIGHT_GREEN).firstCorrectCount(5)
                .feedbackSummary("총평").reviewDueAt(LocalDateTime.of(2026, 11, 10, 9, 30)).build();
        em.persist(leaf);

        topic = LeafTopic.builder().leaf(leaf).topic("미분").status(TopicStatus.RECOVERED).build();
        em.persist(topic);

        quizSet = QuizSet.builder().study(study).leaf(leaf).type(QuizSetType.REVIEW).attemptNo(1).gradedCount(3).build();
        em.persist(quizSet);

        question = Question.builder().quizSet(quizSet).number(1).difficulty(Difficulty.NORMAL)
                .type(QuestionType.MULTIPLE_CHOICE).topic("미분").content("문제 본문")
                .options("[\"보기1\",\"보기2\",\"보기3\",\"보기4\"]").answerIndex(2).explanation("해설")
                .modelAnswer("모범답안").gradingPoints("[\"포인트\"]").build();
        em.persist(question);

        answer = Answer.builder().question(question).selectedIndex(2).essayText("서술").correct(true).aiFeedback("피드백").build();
        em.persist(answer);

        AdCounter adCounter = new AdCounter(user.getId(), AdPlacement.RECORD_VIEW);
        adCounterId = adCounter.getId();
        em.persist(adCounter);

        em.flush();
        em.clear();
    }

    @Test
    void 저장한_값을_그대로_읽는다() {
        Tree savedTree = em.find(Tree.class, tree.getId());
        assertThat(savedTree.getStudyLevel()).isEqualTo(StudyLevel.SELF_STUDY);
        assertThat(savedTree.getShape()).isEqualTo(TreeShape.BASIC);
        assertThat(savedTree.isPaid()).isFalse();
        assertThat(savedTree.getCreatedAt()).isNotNull();
        assertThat(savedTree.getUser().getNickname()).isEqualTo("매핑테스트");

        Study savedStudy = em.find(Study.class, study.getId());
        assertThat(savedStudy.getSubjectName()).isEqualTo("수학");
        assertThat(savedStudy.getStudyLevel()).isEqualTo(StudyLevel.SELF_STUDY);
        assertThat(savedStudy.getTextContent()).isEqualTo("미분 정리");

        assertThat(em.find(StudyImage.class, image.getId()).getS3Key()).isEqualTo("studies/1/a.jpg");

        Leaf savedLeaf = em.find(Leaf.class, leaf.getId());
        assertThat(savedLeaf.getPosition()).isEqualTo(13);
        assertThat(savedLeaf.getLeafType()).isEqualTo(LeafGrade.LIGHT_GREEN);
        assertThat(savedLeaf.getOriginalType()).isEqualTo(LeafGrade.LIGHT_GREEN);
        assertThat(savedLeaf.getReviewDueAt()).isEqualTo(LocalDateTime.of(2026, 11, 10, 9, 30));
        assertThat(savedLeaf.getRecoveredAt()).isNull();

        assertThat(em.find(LeafTopic.class, topic.getId()).getStatus()).isEqualTo(TopicStatus.RECOVERED);

        QuizSet savedSet = em.find(QuizSet.class, quizSet.getId());
        assertThat(savedSet.getType()).isEqualTo(QuizSetType.REVIEW);
        assertThat(savedSet.getStatus()).isEqualTo(QuizSetStatus.CREATED);
        assertThat(savedSet.getLeaf().getId()).isEqualTo(leaf.getId());
        assertThat(savedSet.getCorrectCount()).isNull();

        Question savedQuestion = em.find(Question.class, question.getId());
        assertThat(savedQuestion.getType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(savedQuestion.getDifficulty()).isEqualTo(Difficulty.NORMAL);
        assertThat(savedQuestion.getAnswerIndex()).isEqualTo(2);
        // MySQL JSON 타입은 공백을 정규화해 돌려준다
        assertThat(savedQuestion.getOptions().replace(" ", "")).isEqualTo("[\"보기1\",\"보기2\",\"보기3\",\"보기4\"]");
        assertThat(savedQuestion.getGradingPoints().replace(" ", "")).isEqualTo("[\"포인트\"]");

        Answer savedAnswer = em.find(Answer.class, answer.getId());
        assertThat(savedAnswer.getCorrect()).isTrue();
        assertThat(savedAnswer.getQuestion().getId()).isEqualTo(question.getId());

        assertThat(em.find(AdCounter.class, adCounterId).getViewCount()).isZero();
    }

    @Test
    void 회원을_지우면_DB_CASCADE로_모든_기록이_함께_지워진다() {
        em.remove(em.find(User.class, user.getId()));
        em.flush();
        em.clear();

        assertThat(em.find(Tree.class, tree.getId())).isNull();
        assertThat(em.find(Study.class, study.getId())).isNull();
        assertThat(em.find(StudyImage.class, image.getId())).isNull();
        assertThat(em.find(Leaf.class, leaf.getId())).isNull();
        assertThat(em.find(LeafTopic.class, topic.getId())).isNull();
        assertThat(em.find(QuizSet.class, quizSet.getId())).isNull();
        assertThat(em.find(Question.class, question.getId())).isNull();
        assertThat(em.find(Answer.class, answer.getId())).isNull();
        assertThat(em.find(AdCounter.class, adCounterId)).isNull();
    }

    @Test
    void 나무를_지우면_나무의_기록만_지워지고_회원은_남는다() {
        em.remove(em.find(Tree.class, tree.getId()));
        em.flush();
        em.clear();

        assertThat(em.find(Leaf.class, leaf.getId())).isNull();
        assertThat(em.find(Answer.class, answer.getId())).isNull();
        assertThat(em.find(User.class, user.getId())).isNotNull();
        assertThat(em.find(AdCounter.class, adCounterId)).isNotNull();
    }
}

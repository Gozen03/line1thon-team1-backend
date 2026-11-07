package com.line1thon.jisiknamu.domain.leaf.service;

import com.line1thon.jisiknamu.domain.leaf.dto.ReviewItem;
import com.line1thon.jisiknamu.domain.leaf.dto.ReviewListResponse;
import com.line1thon.jisiknamu.domain.leaf.dto.ReviewQuizResponse;
import com.line1thon.jisiknamu.domain.leaf.dto.TreeReviewListResponse;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafTopic;
import com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafRepository;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafTopicRepository;
import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.quiz.repository.AnswerRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuestionRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.domain.quiz.service.QuizSetResponseMapper;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.repository.TreeRepository;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.ai.GeneratedQuestion;
import com.line1thon.jisiknamu.global.ai.ReviewQuestionsRequest;
import com.line1thon.jisiknamu.global.ai.ReviewQuestionsResult;
import com.line1thon.jisiknamu.global.concurrency.InProgressGuard;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.util.JsonLists;
import com.line1thon.jisiknamu.global.util.KstTime;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int REVIEW_QUESTION_COUNT = 3;
    private static final List<QuizSetType> GRADE_DECIDING_TYPES = List.of(QuizSetType.INITIAL, QuizSetType.RETRY);

    private final LeafRepository leafRepository;
    private final LeafTopicRepository leafTopicRepository;
    private final TreeRepository treeRepository;
    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AiClient aiClient;
    private final InProgressGuard inProgressGuard;
    private final TransactionTemplate transactionTemplate;
    private final QuizSetResponseMapper responseMapper;
    private final JsonLists jsonLists;

    /** R0 */
    @Transactional(readOnly = true)
    public ReviewListResponse getReviews(Long userId) {
        LocalDateTime now = KstTime.now();
        List<Leaf> leaves = leafRepository.findReviewableByUserId(userId, LeafGrade.REVIEWABLE, now);
        Map<Long, List<String>> weakTopics = findWeakTopics(leaves);
        List<ReviewItem> reviews = leaves.stream()
                .map(leaf -> toItem(leaf, leaf.getTree().getId(), leaf.getTree().getSubjectName(), weakTopics))
                .toList();
        return new ReviewListResponse(reviews.size(),
                KstTime.toOffset(leafRepository.findNextReviewAt(userId, LeafGrade.REVIEWABLE, now)), reviews);
    }

    /** R1 */
    @Transactional(readOnly = true)
    public TreeReviewListResponse getTreeReviews(Long userId, Long treeId) {
        Tree tree = treeRepository.findById(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TREE_NOT_FOUND));
        if (!tree.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        List<Leaf> leaves = leafRepository.findReviewableByTreeId(treeId, LeafGrade.REVIEWABLE, KstTime.now());
        Map<Long, List<String>> weakTopics = findWeakTopics(leaves);
        return new TreeReviewListResponse(tree.getId(), tree.getSubjectName(),
                leaves.stream().map(leaf -> toItem(leaf, null, null, weakTopics)).toList());
    }

    /**
     * R2. AI 호출은 최대 50초가 걸리므로 트랜잭션 밖에서 한다. 풀던 복습 세트가 있으면 AI를 부르지 않고 그 세트를 다시 준다.
     */
    public ReviewQuizResponse createReview(Long userId, Long leafId) {
        // 다른 회원의 요청이 내 잎을 막지 못하도록 소유자 확인 뒤에 잠근다
        transactionTemplate.executeWithoutResult(status -> getOwnedLeaf(userId, leafId));
        return inProgressGuard.run("leaf:" + leafId, () -> {
            ReviewSource source = transactionTemplate.execute(status -> prepare(leafId));
            if (source.pending() != null) {
                return source.pending();
            }
            ReviewQuestionsResult result = aiClient.generateReviewQuestions(source.request());
            return transactionTemplate.execute(status -> save(source.treeId(), leafId, result));
        });
    }

    private ReviewSource prepare(Long leafId) {
        Leaf leaf = leafRepository.findWithStudyAndTreeById(leafId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAF_NOT_FOUND));
        requireAvailable(leaf);

        QuizSet pending = quizSetRepository
                .findFirstByLeafIdAndStatusAndType(leafId, QuizSetStatus.CREATED, QuizSetType.REVIEW)
                .orElse(null);
        if (pending != null) {
            return new ReviewSource(leaf.getTree().getId(), toResponse(pending, leafId,
                    questionRepository.findAllByQuizSetIdOrderByNumberAsc(pending.getId())), null);
        }

        Study study = leaf.getStudy();
        String content = study.getTextContent() != null ? study.getTextContent() : study.getExtractedText();
        return new ReviewSource(leaf.getTree().getId(), null, new ReviewQuestionsRequest(
                study.getSubjectName(), study.getStudyLevel(), content, study.getMemo(),
                leafTopicRepository.findTopics(leafId, TopicStatus.WEAK), findWrongQuestions(study),
                questionRepository.findContents(study.getId(), List.of(QuizSetType.REVIEW)), REVIEW_QUESTION_COUNT));
    }

    // 그 잎의 등급을 정한 풀이에서 틀린 객관식 문제들
    private List<ReviewQuestionsRequest.WrongQuestion> findWrongQuestions(Study study) {
        QuizSet gradeSet = quizSetRepository.findFirstByStudyIdAndStatusAndTypeInOrderByIdDesc(
                        study.getId(), QuizSetStatus.SUBMITTED, GRADE_DECIDING_TYPES)
                .orElseThrow();
        return answerRepository.findAllWithQuestionByQuizSetId(gradeSet.getId()).stream()
                .filter(answer -> answer.getQuestion().getType() == QuestionType.MULTIPLE_CHOICE)
                .filter(answer -> !Boolean.TRUE.equals(answer.getCorrect()))
                .map(this::toWrongQuestion)
                .toList();
    }

    private ReviewQuestionsRequest.WrongQuestion toWrongQuestion(Answer answer) {
        Question question = answer.getQuestion();
        return new ReviewQuestionsRequest.WrongQuestion(question.getTopic(), question.getContent(),
                jsonLists.toList(question.getOptions()), question.getAnswerIndex(), answer.getSelectedIndex());
    }

    private ReviewQuizResponse save(Long treeId, Long leafId, ReviewQuestionsResult result) {
        // AI를 기다리는 사이 나무가 삭제됐을 수 있다. 저장하는 동안 삭제되지 않도록 잠근다
        treeRepository.findByIdForUpdate(treeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAF_NOT_FOUND));
        Leaf leaf = leafRepository.findWithStudyAndTreeById(leafId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAF_NOT_FOUND));
        requireAvailable(leaf);

        Study study = leaf.getStudy();
        int attemptNo = quizSetRepository.findMaxAttemptNo(study.getId(), QuizSetType.REVIEW) + 1;
        QuizSet quizSet = quizSetRepository.save(QuizSet.builder()
                .study(study).leaf(leaf).type(QuizSetType.REVIEW).attemptNo(attemptNo)
                .gradedCount(REVIEW_QUESTION_COUNT).build());
        List<Question> questions = questionRepository.saveAll(result.questions().stream()
                .map(generated -> toQuestion(quizSet, generated))
                .toList());
        return toResponse(quizSet, leafId, questions);
    }

    private Question toQuestion(QuizSet quizSet, GeneratedQuestion generated) {
        return Question.builder()
                .quizSet(quizSet)
                .number(generated.number())
                .difficulty(generated.difficulty())
                .type(generated.questionType())
                .topic(generated.topic())
                .content(generated.content())
                .options(jsonLists.toJson(generated.options()))
                .answerIndex(generated.answerIndex())
                .explanation(generated.explanation())
                .build();
    }

    private ReviewQuizResponse toResponse(QuizSet quizSet, Long leafId, List<Question> questions) {
        return new ReviewQuizResponse(quizSet.getId(), quizSet.getType(), leafId, responseMapper.toItems(questions));
    }

    private void getOwnedLeaf(Long userId, Long leafId) {
        Leaf leaf = leafRepository.findWithStudyAndTreeById(leafId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAF_NOT_FOUND));
        if (!leaf.getTree().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireAvailable(Leaf leaf) {
        if (!leaf.isReviewAvailable(KstTime.now())) {
            throw new BusinessException(ErrorCode.REVIEW_NOT_AVAILABLE);
        }
    }

    private Map<Long, List<String>> findWeakTopics(List<Leaf> leaves) {
        List<Long> leafIds = leaves.stream().map(Leaf::getId).toList();
        return leafTopicRepository.findAllByLeafIdInAndStatusOrderByIdAsc(leafIds, TopicStatus.WEAK).stream()
                .collect(Collectors.groupingBy(topic -> topic.getLeaf().getId(),
                        Collectors.mapping(LeafTopic::getTopic, Collectors.toList())));
    }

    private ReviewItem toItem(Leaf leaf, Long treeId, String subjectName, Map<Long, List<String>> weakTopics) {
        return new ReviewItem(leaf.getId(), treeId, subjectName, leaf.getLeafType(),
                KstTime.toOffset(leaf.getStudy().getCreatedAt()), weakTopics.getOrDefault(leaf.getId(), List.of()),
                REVIEW_QUESTION_COUNT);
    }

    private record ReviewSource(Long treeId, ReviewQuizResponse pending, ReviewQuestionsRequest request) {
    }
}

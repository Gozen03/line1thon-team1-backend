package com.line1thon.jisiknamu.domain.quiz.service;

import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.leaf.entity.LeafTopic;
import com.line1thon.jisiknamu.domain.leaf.entity.TopicStatus;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafRepository;
import com.line1thon.jisiknamu.domain.leaf.repository.LeafTopicRepository;
import com.line1thon.jisiknamu.domain.quiz.dto.AnswerSubmitRequest;
import com.line1thon.jisiknamu.domain.quiz.dto.AnswerSubmitRequest.AnswerItem;
import com.line1thon.jisiknamu.domain.quiz.dto.ReviewSubmissionResponse;
import com.line1thon.jisiknamu.domain.quiz.dto.SubmissionResponse;
import com.line1thon.jisiknamu.domain.quiz.dto.SubmissionResult;
import com.line1thon.jisiknamu.domain.quiz.entity.Answer;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.quiz.repository.AnswerRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuestionRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import com.line1thon.jisiknamu.domain.tree.entity.Tree;
import com.line1thon.jisiknamu.domain.tree.repository.TreeRepository;
import com.line1thon.jisiknamu.global.ai.AiClient;
import com.line1thon.jisiknamu.global.ai.FeedbackRequest;
import com.line1thon.jisiknamu.global.ai.FeedbackResult;
import com.line1thon.jisiknamu.global.ai.ReviewFeedbackRequest;
import com.line1thon.jisiknamu.global.concurrency.InProgressGuard;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.util.JsonLists;
import com.line1thon.jisiknamu.global.util.KstTime;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * S2. AI 호출은 최대 50초가 걸리므로 트랜잭션 밖에서 한다: 채점 → AI 피드백 → 저장.
 * AI가 실패하면 아무것도 저장하지 않으므로 같은 답으로 다시 제출할 수 있다.
 */
@Service
public class SubmissionService {

    private static final int ESSAY_MAX_LENGTH = 1000;
    // 시연 모드는 바로 다시 복습할 수 있어서 "내일"처럼 날짜를 단정하지 않는다
    private static final String REVIEW_FAILED_SUMMARY = "아직 조금 헷갈려요. 한 번 더 복습하면 잘할 수 있어요.";

    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final TreeRepository treeRepository;
    private final LeafRepository leafRepository;
    private final LeafTopicRepository leafTopicRepository;
    private final AiClient aiClient;
    private final InProgressGuard inProgressGuard;
    private final TransactionTemplate transactionTemplate;
    private final JsonLists jsonLists;
    private final int reviewDelayDays;
    private final int reviewRetryDelayDays;

    public SubmissionService(QuizSetRepository quizSetRepository, QuestionRepository questionRepository,
                             AnswerRepository answerRepository, TreeRepository treeRepository,
                             LeafRepository leafRepository, LeafTopicRepository leafTopicRepository,
                             AiClient aiClient, InProgressGuard inProgressGuard,
                             TransactionTemplate transactionTemplate, JsonLists jsonLists,
                             @Value("${review.delay-days}") int reviewDelayDays,
                             @Value("${review.retry-delay-days}") int reviewRetryDelayDays) {
        this.quizSetRepository = quizSetRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.treeRepository = treeRepository;
        this.leafRepository = leafRepository;
        this.leafTopicRepository = leafTopicRepository;
        this.aiClient = aiClient;
        this.inProgressGuard = inProgressGuard;
        this.transactionTemplate = transactionTemplate;
        this.jsonLists = jsonLists;
        this.reviewDelayDays = reviewDelayDays;
        this.reviewRetryDelayDays = reviewRetryDelayDays;
    }

    public SubmissionResult submit(Long userId, Long quizSetId, AnswerSubmitRequest request) {
        // 다른 회원의 요청이 내 세트를 막지 못하도록 소유자 확인 뒤에 잠근다
        transactionTemplate.executeWithoutResult(status -> requireOwner(getQuizSet(quizSetId), userId));
        return inProgressGuard.run("quizSet:" + quizSetId, () -> {
            Graded graded = transactionTemplate.execute(status -> grade(quizSetId, request));
            if (graded.quizSetType() == QuizSetType.REVIEW) {
                return submitReview(quizSetId, graded);
            }
            FeedbackResult feedback = aiClient.generateFeedback(toFeedbackRequest(graded));
            return transactionTemplate.execute(status -> save(quizSetId, graded, feedback));
        });
    }

    /**
     * 복습 제출. AI는 성공했을 때만 부르고, 실패하면 고정 문구를 쓰며 잎의 총평은 바꾸지 않는다.
     */
    private ReviewSubmissionResponse submitReview(Long quizSetId, Graded graded) {
        boolean passed = graded.correctCount() == graded.gradedCount();
        String summary = passed
                ? aiClient.generateReviewFeedback(toReviewFeedbackRequest(graded)).summary()
                : REVIEW_FAILED_SUMMARY;
        return transactionTemplate.execute(status -> saveReview(quizSetId, graded, passed, summary));
    }

    private ReviewSubmissionResponse saveReview(Long quizSetId, Graded graded, boolean passed, String summary) {
        // AI를 기다리는 사이 나무가 삭제됐을 수 있다
        treeRepository.findByIdForUpdate(graded.treeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_SET_NOT_FOUND));
        QuizSet quizSet = getQuizSet(quizSetId);
        requireSubmittable(quizSet);
        Leaf leaf = quizSet.getLeaf();
        LocalDateTime now = KstTime.now();

        List<String> recoveredTopics = List.of();
        LocalDateTime reviewDueAt = null;
        if (passed) {
            // 잎이 초록잎이 되므로 출제된 주제만이 아니라 약한 주제 전부를 이해한 것으로 바꾼다 (11/8 결정)
            List<LeafTopic> weakTopics = leafTopicRepository.findAllByLeafIdAndStatusOrderByIdAsc(leaf.getId(), TopicStatus.WEAK);
            weakTopics.forEach(LeafTopic::recover);
            recoveredTopics = weakTopics.stream().map(LeafTopic::getTopic).toList();
            leaf.recover(summary, now);
        } else {
            reviewDueAt = now.plusDays(reviewRetryDelayDays);
            leaf.postponeReview(reviewDueAt);
        }
        saveAnswers(graded, null);
        quizSet.submit(graded.correctCount(), now);

        return new ReviewSubmissionResponse(quizSetId, QuizSetType.REVIEW, graded.correctCount(), graded.gradedCount(), passed,
                new ReviewSubmissionResponse.LeafResult(leaf.getId(), graded.review().beforeGrade(), leaf.getLeafType(),
                        KstTime.toOffset(graded.review().studiedAt()), KstTime.toOffset(reviewDueAt)),
                recoveredTopics, summary,
                leafRepository.countReviewableByTreeId(graded.treeId(), LeafGrade.REVIEWABLE, now),
                graded.questions().stream()
                        .map(question -> toResultItem(question, graded.answers().get(question.getId())))
                        .toList());
    }

    private ReviewFeedbackRequest toReviewFeedbackRequest(Graded graded) {
        return new ReviewFeedbackRequest(graded.subjectName(), graded.review().originalSummary(),
                KstTime.toOffset(graded.review().studiedAt()), KstTime.toOffset(KstTime.now()), true,
                graded.correctCount(), graded.gradedCount(),
                graded.questions().stream()
                        .map(question -> new ReviewFeedbackRequest.TopicResult(question.getTopic(),
                                isCorrect(question, graded.answers().get(question.getId()))))
                        .toList());
    }

    private Graded grade(Long quizSetId, AnswerSubmitRequest request) {
        QuizSet quizSet = getQuizSet(quizSetId);
        requireSubmittable(quizSet);
        Study study = quizSet.getStudy();
        List<Question> questions = questionRepository.findAllByQuizSetIdOrderByNumberAsc(quizSetId);
        Map<Long, AnswerItem> answers = validateAnswers(questions, request);

        int correctCount = 0;
        int gradedCount = 0;
        Set<String> strengths = new LinkedHashSet<>();
        Set<String> weaknesses = new LinkedHashSet<>();
        for (Question question : questions) {
            if (question.getType() != QuestionType.MULTIPLE_CHOICE) {
                continue;
            }
            gradedCount++;
            if (isCorrect(question, answers.get(question.getId()))) {
                correctCount++;
                strengths.add(question.getTopic());
            } else {
                weaknesses.add(question.getTopic());
            }
        }
        // 같은 주제를 맞히기도 틀리기도 했으면 약한 주제로 본다
        strengths.removeAll(weaknesses);

        ReviewInfo review = quizSet.getType() == QuizSetType.REVIEW
                ? new ReviewInfo(quizSet.getLeaf().getLeafType(), quizSet.getLeaf().getFeedbackSummary(), study.getCreatedAt())
                : null;
        return new Graded(study.getTree().getId(), quizSet.getType(), study.getSubjectName(), study.getStudyLevel(),
                questions, answers, correctCount, gradedCount, LeafGrade.fromCorrectCount(correctCount),
                List.copyOf(strengths), List.copyOf(weaknesses), review);
    }

    private SubmissionResponse save(Long quizSetId, Graded graded, FeedbackResult feedback) {
        // AI를 기다리는 사이 나무가 삭제되거나 새 공부로 세트가 지워졌을 수 있다
        Tree tree = treeRepository.findByIdForUpdate(graded.treeId())
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_SET_NOT_FOUND));
        QuizSet quizSet = getQuizSet(quizSetId);
        requireSubmittable(quizSet);

        LocalDateTime now = KstTime.now();
        LocalDateTime reviewDueAt = LeafGrade.REVIEWABLE.contains(graded.grade()) ? now.plusDays(reviewDelayDays) : null;
        Leaf leaf = quizSet.getType() == QuizSetType.INITIAL
                ? createLeaf(tree, quizSet.getStudy(), graded, feedback, reviewDueAt)
                : applyRetry(quizSet.getStudy(), graded, feedback, reviewDueAt);
        saveTopics(leaf, graded);
        saveAnswers(graded, feedback.essayFeedback());
        quizSet.submit(graded.correctCount(), now);

        return toResponse(quizSetId, graded, feedback, leaf, reviewDueAt, leafRepository.countByTreeId(tree.getId()));
    }

    private Leaf createLeaf(Tree tree, Study study, Graded graded, FeedbackResult feedback, LocalDateTime reviewDueAt) {
        int position = leafRepository.findMaxPositionByTreeId(tree.getId()) + 1;
        if (position > Leaf.MAX_PER_TREE) {
            throw new BusinessException(ErrorCode.TREE_FULL);
        }
        return leafRepository.save(Leaf.builder()
                .tree(tree).study(study).position(position).grade(graded.grade())
                .firstCorrectCount(graded.correctCount()).feedbackSummary(feedback.summary())
                .reviewDueAt(reviewDueAt)
                .build());
    }

    private Leaf applyRetry(Study study, Graded graded, FeedbackResult feedback, LocalDateTime reviewDueAt) {
        Leaf leaf = getBudLeaf(study);
        leaf.applyRetry(graded.grade(), graded.correctCount(), feedback.summary(), reviewDueAt);
        // 잎의 주제 목록은 재도전 결과로 다시 만든다
        leafTopicRepository.deleteAllByLeafId(leaf.getId());
        leafTopicRepository.flush();
        return leaf;
    }

    private void saveTopics(Leaf leaf, Graded graded) {
        graded.strengths().forEach(topic -> leafTopicRepository.save(
                LeafTopic.builder().leaf(leaf).topic(topic).status(TopicStatus.STRONG).build()));
        graded.weaknesses().forEach(topic -> leafTopicRepository.save(
                LeafTopic.builder().leaf(leaf).topic(topic).status(TopicStatus.WEAK).build()));
    }

    private void saveAnswers(Graded graded, String essayFeedback) {
        for (Question question : graded.questions()) {
            AnswerItem answer = graded.answers().get(question.getId());
            boolean multipleChoice = question.getType() == QuestionType.MULTIPLE_CHOICE;
            answerRepository.save(Answer.builder()
                    .question(questionRepository.getReferenceById(question.getId()))
                    .selectedIndex(multipleChoice ? answer.selectedIndex() : null)
                    .essayText(multipleChoice ? null : essayText(answer))
                    .correct(multipleChoice ? isCorrect(question, answer) : null)
                    .aiFeedback(multipleChoice ? null : essayFeedback)
                    .build());
        }
    }

    private FeedbackRequest toFeedbackRequest(Graded graded) {
        List<FeedbackRequest.QuestionResult> results = graded.questions().stream()
                .filter(question -> question.getType() == QuestionType.MULTIPLE_CHOICE)
                .map(question -> new FeedbackRequest.QuestionResult(question.getNumber(), question.getTopic(),
                        question.getDifficulty(), isCorrect(question, graded.answers().get(question.getId()))))
                .toList();
        Question essay = graded.questions().stream()
                .filter(question -> question.getType() == QuestionType.ESSAY)
                .findFirst()
                .orElseThrow();
        return new FeedbackRequest(graded.subjectName(), graded.studyLevel(), graded.correctCount(), graded.gradedCount(),
                graded.grade(), results, new FeedbackRequest.Essay(essay.getContent(), essay.getModelAnswer(),
                jsonLists.toList(essay.getGradingPoints()), essayText(graded.answers().get(essay.getId()))));
    }

    private SubmissionResponse toResponse(Long quizSetId, Graded graded, FeedbackResult feedback, Leaf leaf,
                                          LocalDateTime reviewDueAt, long treeLeafCount) {
        List<SubmissionResponse.ResultItem> results = graded.questions().stream()
                .map(question -> toResultItem(question, graded.answers().get(question.getId())))
                .toList();
        return new SubmissionResponse(quizSetId, graded.quizSetType(), graded.correctCount(), graded.gradedCount(),
                new SubmissionResponse.LeafResult(leaf.getId(), graded.treeId(), leaf.getPosition(), graded.grade(),
                        graded.grade().getMessage(), reviewDueAt != null, KstTime.toOffset(reviewDueAt), treeLeafCount),
                new SubmissionResponse.Feedback(feedback.summary(), graded.strengths(), graded.weaknesses(),
                        feedback.essayFeedback()),
                results);
    }

    private SubmissionResponse.ResultItem toResultItem(Question question, AnswerItem answer) {
        boolean multipleChoice = question.getType() == QuestionType.MULTIPLE_CHOICE;
        return new SubmissionResponse.ResultItem(question.getId(), question.getNumber(), question.getTopic(),
                question.getDifficulty(), question.getType(),
                multipleChoice ? answer.selectedIndex() : null,
                question.getAnswerIndex(),
                multipleChoice ? isCorrect(question, answer) : null,
                question.getExplanation(),
                multipleChoice ? null : essayText(answer),
                question.getModelAnswer());
    }

    private Map<Long, AnswerItem> validateAnswers(List<Question> questions, AnswerSubmitRequest request) {
        List<AnswerItem> items = request.answers();
        if (items == null || items.size() != questions.size()) {
            throw new BusinessException(ErrorCode.ANSWER_COUNT_MISMATCH);
        }
        Map<Long, AnswerItem> answers = new HashMap<>();
        for (AnswerItem item : items) {
            if (item == null || item.questionId() == null || answers.put(item.questionId(), item) != null) {
                throw new BusinessException(ErrorCode.ANSWER_COUNT_MISMATCH);
            }
        }
        for (Question question : questions) {
            AnswerItem answer = answers.get(question.getId());
            if (answer == null) {
                throw new BusinessException(ErrorCode.ANSWER_COUNT_MISMATCH);
            }
            validateAnswerValue(question, answer);
        }
        return answers;
    }

    private void validateAnswerValue(Question question, AnswerItem answer) {
        if (question.getType() == QuestionType.MULTIPLE_CHOICE) {
            Integer selected = answer.selectedIndex();
            if (selected != null && (selected < 0 || selected > 3)) {
                throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
            return;
        }
        String essay = essayText(answer);
        if (essay != null && essay.codePointCount(0, essay.length()) > ESSAY_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private boolean isCorrect(Question question, AnswerItem answer) {
        return answer.selectedIndex() != null && answer.selectedIndex().equals(question.getAnswerIndex());
    }

    private String essayText(AnswerItem answer) {
        return answer.essayText() == null || answer.essayText().isBlank() ? null : answer.essayText().strip();
    }

    private QuizSet getQuizSet(Long quizSetId) {
        return quizSetRepository.findWithStudyAndTreeById(quizSetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_SET_NOT_FOUND));
    }

    private void requireOwner(QuizSet quizSet, Long userId) {
        if (!quizSet.getStudy().getTree().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private void requireSubmittable(QuizSet quizSet) {
        if (quizSet.getStatus() == QuizSetStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }
        if (quizSet.getType() == QuizSetType.RETRY) {
            getBudLeaf(quizSet.getStudy());
        }
    }

    private Leaf getBudLeaf(Study study) {
        return leafRepository.findByStudyId(study.getId())
                .filter(leaf -> leaf.getLeafType() == LeafGrade.BUD)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_BUD_RESULT));
    }

    private record Graded(Long treeId, QuizSetType quizSetType, String subjectName, StudyLevel studyLevel,
                          List<Question> questions, Map<Long, AnswerItem> answers, int correctCount, int gradedCount,
                          LeafGrade grade, List<String> strengths, List<String> weaknesses, ReviewInfo review) {
    }

    private record ReviewInfo(LeafGrade beforeGrade, String originalSummary, LocalDateTime studiedAt) {
    }
}

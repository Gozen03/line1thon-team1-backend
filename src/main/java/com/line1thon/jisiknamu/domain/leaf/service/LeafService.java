package com.line1thon.jisiknamu.domain.leaf.service;

import com.line1thon.jisiknamu.domain.leaf.dto.LeafDetailResponse;
import com.line1thon.jisiknamu.domain.leaf.entity.Leaf;
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
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.domain.study.repository.StudyImageRepository;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import com.line1thon.jisiknamu.global.storage.ImageStorage;
import com.line1thon.jisiknamu.global.util.JsonLists;
import com.line1thon.jisiknamu.global.util.KstTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LeafService {

    private static final List<QuizSetType> GRADE_DECIDING_TYPES = List.of(QuizSetType.INITIAL, QuizSetType.RETRY);
    // 복습으로 이해한 주제도 그날의 약한 주제 기록으로 보여 준다
    private static final List<TopicStatus> WEAK_RECORD_STATUSES = List.of(TopicStatus.WEAK, TopicStatus.RECOVERED);

    private final LeafRepository leafRepository;
    private final LeafTopicRepository leafTopicRepository;
    private final QuizSetRepository quizSetRepository;
    private final AnswerRepository answerRepository;
    private final StudyImageRepository studyImageRepository;
    private final ImageStorage imageStorage;
    private final JsonLists jsonLists;

    @Transactional(readOnly = true)
    public LeafDetailResponse getLeaf(Long userId, Long leafId) {
        Leaf leaf = leafRepository.findWithStudyAndTreeById(leafId)
                .orElseThrow(() -> new BusinessException(ErrorCode.LEAF_NOT_FOUND));
        if (!leaf.getTree().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        Study study = leaf.getStudy();
        QuizSet gradeSet = quizSetRepository.findFirstByStudyIdAndStatusAndTypeInOrderByIdDesc(
                        study.getId(), QuizSetStatus.SUBMITTED, GRADE_DECIDING_TYPES)
                .orElseThrow();
        List<Answer> answers = answerRepository.findAllWithQuestionByQuizSetId(gradeSet.getId());
        String essayFeedback = answers.stream()
                .filter(answer -> answer.getQuestion().getType() == QuestionType.ESSAY)
                .map(Answer::getAiFeedback)
                .findFirst()
                .orElse(null);

        return new LeafDetailResponse(leaf.getId(), leaf.getTree().getId(), study.getSubjectName(), leaf.getPosition(),
                leaf.getLeafType(), leaf.getOriginalType(), leaf.getFirstCorrectCount(),
                KstTime.toOffset(study.getCreatedAt()), leaf.isReviewed(), KstTime.toOffset(leaf.getRecoveredAt()),
                new LeafDetailResponse.StudyInfo(study.getInputType(),
                        studyImageRepository.findS3KeysByStudyId(study.getId()).stream().map(imageStorage::createReadUrl).toList(),
                        study.getTextContent(), study.getMemo()),
                answers.stream().map(this::toQuestionItem).toList(),
                new LeafDetailResponse.Feedback(leaf.getFeedbackSummary(),
                        leafTopicRepository.findTopics(leaf.getId(), TopicStatus.STRONG),
                        leafTopicRepository.findTopicsIn(leaf.getId(), WEAK_RECORD_STATUSES),
                        essayFeedback),
                quizSetRepository.findAllByStudyIdAndStatusOrderByIdAsc(study.getId(), QuizSetStatus.SUBMITTED).stream()
                        .map(quizSet -> new LeafDetailResponse.QuizSetItem(quizSet.getId(), quizSet.getType(),
                                quizSet.getCorrectCount(), KstTime.toOffset(quizSet.getSubmittedAt())))
                        .toList());
    }

    private LeafDetailResponse.QuestionItem toQuestionItem(Answer answer) {
        Question question = answer.getQuestion();
        return new LeafDetailResponse.QuestionItem(question.getNumber(), question.getTopic(), question.getType(),
                question.getContent(), jsonLists.toList(question.getOptions()), answer.getSelectedIndex(),
                question.getAnswerIndex(), answer.getCorrect(), question.getExplanation(), answer.getEssayText(),
                question.getModelAnswer());
    }
}

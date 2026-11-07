package com.line1thon.jisiknamu.domain.quiz.service;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetStatus;
import com.line1thon.jisiknamu.domain.quiz.repository.QuestionRepository;
import com.line1thon.jisiknamu.domain.quiz.repository.QuizSetRepository;
import com.line1thon.jisiknamu.global.error.BusinessException;
import com.line1thon.jisiknamu.global.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class QuizSetService {

    private final QuizSetRepository quizSetRepository;
    private final QuestionRepository questionRepository;
    private final QuizSetResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public QuizSetResponse getQuizSet(Long userId, Long quizSetId) {
        QuizSet quizSet = quizSetRepository.findWithStudyAndTreeById(quizSetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.QUIZ_SET_NOT_FOUND));
        if (!quizSet.getStudy().getTree().getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (quizSet.getStatus() == QuizSetStatus.SUBMITTED) {
            throw new BusinessException(ErrorCode.ALREADY_SUBMITTED);
        }
        return responseMapper.toResponse(quizSet, questionRepository.findAllByQuizSetIdOrderByNumberAsc(quizSetId));
    }
}

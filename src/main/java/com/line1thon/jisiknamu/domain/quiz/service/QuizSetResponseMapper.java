package com.line1thon.jisiknamu.domain.quiz.service;

import com.line1thon.jisiknamu.domain.quiz.dto.QuizSetResponse;
import com.line1thon.jisiknamu.domain.quiz.entity.Question;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSet;
import com.line1thon.jisiknamu.domain.study.entity.Study;
import com.line1thon.jisiknamu.global.util.JsonLists;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QuizSetResponseMapper {

    private final JsonLists jsonLists;

    public QuizSetResponse toResponse(QuizSet quizSet, List<Question> questions) {
        Study study = quizSet.getStudy();
        return new QuizSetResponse(study.getId(), quizSet.getId(), quizSet.getType(), study.getTree().getId(),
                study.getSubjectName(), study.getStudyLevel(), toItems(questions));
    }

    public List<QuizSetResponse.QuestionItem> toItems(List<Question> questions) {
        return questions.stream()
                .map(question -> new QuizSetResponse.QuestionItem(question.getId(), question.getNumber(),
                        question.getDifficulty(), question.getType(), question.getTopic(),
                        question.getContent(), jsonLists.toList(question.getOptions())))
                .toList();
    }
}

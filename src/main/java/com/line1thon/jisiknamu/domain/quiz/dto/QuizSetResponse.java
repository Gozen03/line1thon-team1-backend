package com.line1thon.jisiknamu.domain.quiz.dto;

import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import java.util.List;

/**
 * S1·S3·S4 공통 응답. 정답·해설·모범답안은 넣지 않는다.
 */
public record QuizSetResponse(Long studyId, Long quizSetId, QuizSetType quizSetType, Long treeId,
                              String subjectName, StudyLevel studyLevel, List<QuestionItem> questions) {

    public record QuestionItem(Long questionId, int number, Difficulty difficulty, QuestionType questionType,
                               String topic, String content, List<String> options) {
    }
}

package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.leaf.entity.LeafGrade;
import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import java.util.List;

/**
 * AI2 요청. results는 객관식 6문제 모두다.
 */
public record FeedbackRequest(String subjectName, StudyLevel studyLevel, int correctCount, int gradedCount,
                              LeafGrade grade, List<QuestionResult> results, Essay essay) {

    public record QuestionResult(int number, String topic, Difficulty difficulty, boolean isCorrect) {
    }

    public record Essay(String content, String modelAnswer, List<String> gradingPoints, String essayText) {
    }
}

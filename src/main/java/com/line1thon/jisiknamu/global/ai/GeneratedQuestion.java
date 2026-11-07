package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.quiz.entity.Difficulty;
import com.line1thon.jisiknamu.domain.quiz.entity.QuestionType;
import java.util.List;

public record GeneratedQuestion(int number, Difficulty difficulty, QuestionType questionType, String topic,
                                String content, List<String> options, Integer answerIndex, String explanation,
                                String modelAnswer, List<String> gradingPoints) {
}

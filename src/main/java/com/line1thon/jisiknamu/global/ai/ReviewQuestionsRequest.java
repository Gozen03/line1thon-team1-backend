package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import java.util.List;

/**
 * AI3 요청. wrongQuestions는 그 잎의 등급을 정한 풀이에서 틀린 객관식 문제들이다.
 */
public record ReviewQuestionsRequest(String subjectName, StudyLevel studyLevel, String content, String memo,
                                     List<String> weakTopics, List<WrongQuestion> wrongQuestions,
                                     List<String> excludeQuestions, int count) {

    public record WrongQuestion(String topic, String content, List<String> options, int answerIndex,
                                Integer selectedIndex) {
    }
}

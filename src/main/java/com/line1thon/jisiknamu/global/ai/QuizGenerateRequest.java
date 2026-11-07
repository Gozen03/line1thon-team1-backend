package com.line1thon.jisiknamu.global.ai;

import com.line1thon.jisiknamu.domain.quiz.entity.QuizSetType;
import com.line1thon.jisiknamu.domain.study.entity.InputType;
import com.line1thon.jisiknamu.domain.tree.entity.StudyLevel;
import java.util.List;

/**
 * AI1 요청. 재도전은 항상 TEXT이고 content에 저장해 둔 공부 내용을 넣는다.
 * weakTopics는 재도전의 직전 약한 주제, excludeQuestions는 이전 세트의 문제 본문이다.
 */
public record QuizGenerateRequest(QuizSetType quizSetType, String subjectName, StudyLevel studyLevel,
                                  InputType inputType, List<String> imageUrls, String content, String memo,
                                  List<String> weakTopics, List<String> excludeQuestions) {
}

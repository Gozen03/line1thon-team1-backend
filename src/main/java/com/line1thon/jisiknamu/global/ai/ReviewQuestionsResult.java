package com.line1thon.jisiknamu.global.ai;

import java.util.List;

/**
 * AI3 응답. 각 문제의 topic은 요청한 weakTopics 중 하나와 글자까지 같다.
 */
public record ReviewQuestionsResult(List<GeneratedQuestion> questions) {
}

package com.line1thon.jisiknamu.global.ai;

import java.util.List;

/**
 * AI1 응답. extractedText는 IMAGE일 때만 값이 있다. 사진을 못 읽으면 응답 대신 NOTE_UNREADABLE을 던진다.
 */
public record GeneratedQuiz(String extractedText, List<GeneratedQuestion> questions) {
}

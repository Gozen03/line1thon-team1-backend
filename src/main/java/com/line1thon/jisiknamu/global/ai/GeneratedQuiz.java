package com.line1thon.jisiknamu.global.ai;

import java.util.List;

/**
 * API 명세서 11장의 문제 출제 응답.
 */
public record GeneratedQuiz(boolean readable, String extractedText, List<GeneratedQuestion> questions) {

    public static GeneratedQuiz unreadable() {
        return new GeneratedQuiz(false, null, List.of());
    }
}

package com.line1thon.jisiknamu.global.ai;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * AI4 요청. 복습에 성공했을 때만 부른다.
 */
public record ReviewFeedbackRequest(String subjectName, String originalSummary, OffsetDateTime studiedAt,
                                    OffsetDateTime reviewedAt, boolean passed, int correctCount, int gradedCount,
                                    List<TopicResult> results) {

    public record TopicResult(String topic, boolean isCorrect) {
    }
}

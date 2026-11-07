package com.line1thon.jisiknamu.domain.quiz.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * S2 응답. quizSetType이 INITIAL·RETRY면 SubmissionResponse, REVIEW면 ReviewSubmissionResponse다.
 */
@Schema(oneOf = {SubmissionResponse.class, ReviewSubmissionResponse.class})
public sealed interface SubmissionResult permits SubmissionResponse, ReviewSubmissionResponse {
}

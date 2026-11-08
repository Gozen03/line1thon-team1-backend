package com.line1thon.jisiknamu.global.ai;

/**
 * AI 서버 호출 (AI 서버 API 명세의 AI1~AI4). ai.server.mock=true면 MockAiClient, false면 HttpAiClient를 쓴다.
 * 구현이 AI 서버를 부를 때는 기본 경로 /ai/v1, X-Internal-Key 헤더, 타임아웃 50초를 쓴다.
 * 실패는 BusinessException으로 던진다: NOTE_UNREADABLE, CONTENT_TOO_SHORT, AI_SERVER_ERROR, AI_TIMEOUT.
 */
public interface AiClient {

    /** AI1. POST /questions (S1, S3) */
    GeneratedQuiz generateQuiz(QuizGenerateRequest request);

    /** AI2. POST /feedback (S2 첫 풀이·재도전) */
    FeedbackResult generateFeedback(FeedbackRequest request);

    /** AI3. POST /review-questions (R2) */
    ReviewQuestionsResult generateReviewQuestions(ReviewQuestionsRequest request);

    /** AI4. POST /review-feedback (S2 복습 성공) */
    ReviewFeedbackResult generateReviewFeedback(ReviewFeedbackRequest request);
}

package com.line1thon.jisiknamu.global.ai;

/**
 * AI 서버 호출. AI 서버 API 명세가 나오면 HTTP 구현을 추가한다 (ai.server.mock=false).
 * 실패는 BusinessException으로 던진다: AI_SERVER_ERROR, AI_TIMEOUT, CONTENT_TOO_SHORT.
 * 구현이 AI 서버를 부를 때는 X-Internal-Key 헤더와 타임아웃 50초를 쓴다.
 */
public interface AiClient {

    /**
     * 사진을 읽을 수 없으면 readable이 false인 결과를 돌려준다.
     */
    GeneratedQuiz generateQuiz(QuizGenerateRequest request);
}

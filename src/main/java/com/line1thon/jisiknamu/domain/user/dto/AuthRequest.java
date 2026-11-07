package com.line1thon.jisiknamu.domain.user.dto;

/**
 * 가입(U1)·로그인(U2) 요청. 값 검사는 명세의 에러 코드로 응답하도록 UserService에서 한다.
 */
public record AuthRequest(String nickname, String password) {
}

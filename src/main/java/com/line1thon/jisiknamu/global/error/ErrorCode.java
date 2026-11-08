package com.line1thon.jisiknamu.global.error;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * API 명세서 4장의 에러 코드. 이름이 그대로 응답의 error.code가 된다.
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 공통
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버에 문제가 생겼습니다. 잠시 후 다시 시도해 주세요."),

    // 회원
    NICKNAME_INVALID(HttpStatus.BAD_REQUEST, "닉네임은 2~10자로 입력해 주세요."),
    PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "비밀번호는 숫자 4~6자리로 입력해 주세요."),
    NICKNAME_DUPLICATED(HttpStatus.CONFLICT, "이미 사용 중인 닉네임입니다."),
    LOGIN_FAILED(HttpStatus.UNAUTHORIZED, "닉네임 또는 비밀번호가 올바르지 않습니다."),

    // 나무
    TREE_NOT_FOUND(HttpStatus.NOT_FOUND, "나무를 찾을 수 없습니다."),
    TREE_LIMIT_EXCEEDED(HttpStatus.FORBIDDEN, "무료 나무는 3그루까지입니다."),
    TREE_SHAPE_LOCKED(HttpStatus.FORBIDDEN, "아직 열리지 않은 나무 모양입니다."),
    TREE_FULL(HttpStatus.CONFLICT, "나무에 잎이 가득 찼습니다."),

    // 공부 기록·문제
    STUDY_NOT_FOUND(HttpStatus.NOT_FOUND, "공부 기록을 찾을 수 없습니다."),
    STUDY_INPUT_EMPTY(HttpStatus.BAD_REQUEST, "사진이나 텍스트를 입력해 주세요."),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "이미지 형식, 용량 또는 장수를 확인해 주세요."),
    NOTE_UNREADABLE(HttpStatus.UNPROCESSABLE_CONTENT, "사진을 읽을 수 없습니다. 공부 내용이 잘 보이게 다시 찍어 주세요."),
    CONTENT_TOO_SHORT(HttpStatus.UNPROCESSABLE_CONTENT, "문제를 만들기에는 공부 내용이 너무 적습니다."),
    QUIZ_SET_NOT_FOUND(HttpStatus.NOT_FOUND, "문제 세트를 찾을 수 없습니다."),
    ANSWER_COUNT_MISMATCH(HttpStatus.BAD_REQUEST, "제출한 답이 문제 세트와 맞지 않습니다."),
    ALREADY_SUBMITTED(HttpStatus.CONFLICT, "이미 제출한 문제 세트입니다."),
    REQUEST_IN_PROGRESS(HttpStatus.CONFLICT, "요청을 처리하고 있습니다. 잠시만 기다려 주세요."),
    NOT_BUD_RESULT(HttpStatus.CONFLICT, "잎눈일 때만 재도전할 수 있습니다."),

    // 잎·복습
    LEAF_NOT_FOUND(HttpStatus.NOT_FOUND, "잎을 찾을 수 없습니다."),
    REVIEW_NOT_AVAILABLE(HttpStatus.CONFLICT, "지금은 복습할 수 없는 잎입니다."),

    // AI 서버
    AI_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "AI 서버에 문제가 생겼습니다. 잠시 후 다시 시도해 주세요."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "AI 서버 응답이 늦어지고 있습니다. 잠시 후 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}

package com.line1thon.jisiknamu.global.response;

import com.line1thon.jisiknamu.global.error.ErrorCode;

/**
 * 모든 응답의 공통 형식: { success, data, error }. 204 응답은 본문 없이 보낸다.
 */
public record ApiResponse<T>(boolean success, T data, ErrorBody error) {

    public record ErrorBody(String code, String message) {
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return fail(errorCode, errorCode.getMessage());
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorBody(errorCode.name(), message));
    }
}

package com.line1thon.jisiknamu.global.error;

import lombok.Getter;

/**
 * 서비스 코드에서 throw new BusinessException(ErrorCode.TREE_FULL)처럼 쓴다.
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}

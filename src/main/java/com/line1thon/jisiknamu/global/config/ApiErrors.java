package com.line1thon.jisiknamu.global.config;

import com.line1thon.jisiknamu.global.error.ErrorCode;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 이 API가 낼 수 있는 에러 코드. Swagger에 HTTP 상태별 응답 예시로 나온다.
 * 토큰이 필요한 API의 UNAUTHORIZED는 자동으로 붙으므로 적지 않는다.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrors {

    ErrorCode[] value();
}

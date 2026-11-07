package com.line1thon.jisiknamu.global.error;

import com.line1thon.jisiknamu.global.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * 모든 에러를 { success: false, data: null, error } 형식으로 바꾼다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception e) {
        log.error("처리하지 못한 예외", e);
        ErrorCode errorCode = ErrorCode.INTERNAL_ERROR;
        return ResponseEntity.status(errorCode.getStatus()).body(ApiResponse.fail(errorCode));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(ErrorCode.INVALID_REQUEST.getMessage());
        return handleExceptionInternal(ex, ApiResponse.fail(ErrorCode.INVALID_REQUEST, message), headers, status, request);
    }

    // 업로드 용량 초과는 스프링 기본값 413 대신 명세의 IMAGE_INVALID(400)로 준다
    @Override
    protected ResponseEntity<Object> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return handleExceptionInternal(ex, ApiResponse.fail(ErrorCode.IMAGE_INVALID), headers, HttpStatus.BAD_REQUEST, request);
    }

    // 스프링 MVC가 던지는 나머지 예외(JSON 형식 오류, 없는 경로, 지원하지 않는 메서드 등)가 모두 여기로 온다
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Object responseBody = body instanceof ApiResponse<?> ? body : toResponseBody(ex, status);
        HttpHeaders jsonHeaders = new HttpHeaders();
        jsonHeaders.putAll(headers);
        jsonHeaders.setContentType(MediaType.APPLICATION_JSON);
        return super.handleExceptionInternal(ex, responseBody, jsonHeaders, status, request);
    }

    private ApiResponse<Void> toResponseBody(Exception ex, HttpStatusCode status) {
        if (status.is5xxServerError()) {
            log.error("스프링 MVC 처리 중 서버 오류", ex);
            return ApiResponse.fail(ErrorCode.INTERNAL_ERROR);
        }
        // 명세에 404·405·415 전용 코드가 없어 HTTP 상태는 그대로 두고 INVALID_REQUEST로 응답한다
        if (status.value() == HttpStatus.NOT_FOUND.value()) {
            return ApiResponse.fail(ErrorCode.INVALID_REQUEST, "요청한 경로를 찾을 수 없습니다.");
        }
        if (status.value() == HttpStatus.METHOD_NOT_ALLOWED.value()) {
            return ApiResponse.fail(ErrorCode.INVALID_REQUEST, "지원하지 않는 메서드입니다.");
        }
        return ApiResponse.fail(ErrorCode.INVALID_REQUEST);
    }
}

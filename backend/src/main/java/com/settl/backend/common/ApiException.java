package com.settl.backend.common;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public ApiException(String message, HttpStatus status, String errorCode) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public ApiException(String message, HttpStatus status) {
        this(message, status, status.name());
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static ApiException badRequest(String message, String errorCode) {
        return new ApiException(message, HttpStatus.BAD_REQUEST, errorCode);
    }

    public static ApiException unauthorized(String message, String errorCode) {
        return new ApiException(message, HttpStatus.UNAUTHORIZED, errorCode);
    }

    public static ApiException forbidden(String message, String errorCode) {
        return new ApiException(message, HttpStatus.FORBIDDEN, errorCode);
    }

    public static ApiException notFound(String message, String errorCode) {
        return new ApiException(message, HttpStatus.NOT_FOUND, errorCode);
    }

    public static ApiException conflict(String message, String errorCode) {
        return new ApiException(message, HttpStatus.CONFLICT, errorCode);
    }
}

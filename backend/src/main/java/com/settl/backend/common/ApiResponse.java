package com.settl.backend.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        String errorCode,
        T data,
        Map<String, String> errors,
        Instant timestamp
) {

    public ApiResponse(boolean success, String message, String errorCode, T data, Map<String, String> errors) {
        this(success, message, errorCode, data, errors, Instant.now());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, message, null, data, null);
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "Operation successful");
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return new ApiResponse<>(false, message, errorCode, null, null);
    }

    public static <T> ApiResponse<T> validationError(String message, Map<String, String> errors) {
        return new ApiResponse<>(false, message, "VALIDATION_FAILED", null, errors);
    }
}

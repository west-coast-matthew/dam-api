package com.twofourseven.dam.vo;

import java.time.Instant;
import java.util.List;

/**
 * Standard error body returned by {@link com.twofourseven.dam.exceptions.GlobalExceptionHandler}.
 */
public record ErrorResponseVO(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details) {

    public static ErrorResponseVO of(int status, String error, String message, String path) {
        return new ErrorResponseVO(Instant.now(), status, error, message, path, List.of());
    }

    public static ErrorResponseVO of(int status, String error, String message, String path, List<String> details) {
        return new ErrorResponseVO(Instant.now(), status, error, message, path, List.copyOf(details));
    }
}

package com.kingsfarm.kingsfarmbackend.common;

import java.time.Instant;
import java.util.List;

/**
 * The single error shape every failed request gets back — never a raw stack
 * trace or exception message, so nothing internal leaks to a client.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        List<String> details
) {
    public ApiError(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path, null);
    }

    public ApiError(int status, String error, String message, String path, List<String> details) {
        this(Instant.now(), status, error, message, path, details);
    }
}

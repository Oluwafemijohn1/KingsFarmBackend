package com.kingsfarm.kingsfarmbackend.common;

import com.kingsfarm.kingsfarmbackend.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * One place that turns every exception into the same {@link ApiError} shape
 * — nothing internal (stack traces, raw exception messages from libraries,
 * SQL errors) ever reaches a client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        return build(HttpStatus.FORBIDDEN, "You don't have permission to do that.", req, null);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> handleInvalidCredentials(RuntimeException ex, HttpServletRequest req) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleLocked(AccountLockedException ex, HttpServletRequest req) {
        return build(HttpStatus.LOCKED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(OnLeaveException.class)
    public ResponseEntity<ApiError> handleOnLeave(OnLeaveException ex, HttpServletRequest req) {
        return build(HttpStatus.LOCKED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ApiError> handlePasswordChangeRequired(PasswordChangeRequiredException ex, HttpServletRequest req) {
        ApiError body = new ApiError(HttpStatus.FORBIDDEN.value(), "PASSWORD_CHANGE_REQUIRED", ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed.", req, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        // Deliberately generic — the real exception is logged server-side by
        // the container/logging framework, never echoed back to the caller.
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}

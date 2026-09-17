package com.kingsfarm.kingsfarmbackend.common;

import com.kingsfarm.kingsfarmbackend.common.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

/**
 * One place that turns every exception into the same {@link ApiError} shape
 * — nothing internal (stack traces, raw exception messages from libraries,
 * SQL errors) ever reaches a client. Every branch also logs server-side
 * (previously none of them did, despite a couple of comments claiming
 * otherwise) — a 500 with no log line is unreproducible after the fact, and
 * a burst of "session expired" 401s is otherwise invisible unless someone
 * happens to be looking at a browser console when it happens.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Best-effort "who was making this request" for log context — "anonymous" for a request with no real session. */
    private static String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRealSession = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        return hasRealSession ? auth.getName() : "anonymous";
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(NotFoundException ex, HttpServletRequest req) {
        log.debug("404 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException ex, HttpServletRequest req) {
        log.info("409 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.CONFLICT, ex.getMessage(), req, null);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> handleBadRequest(BadRequestException ex, HttpServletRequest req) {
        log.info("400 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), req, null);
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> handleForbidden(ForbiddenException ex, HttpServletRequest req) {
        log.warn("403 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), req, null);
    }

    /**
     * Both "no valid session at all" (missing/invalid/expired access token —
     * JwtAuthenticationFilter left the SecurityContext anonymous) and "signed
     * in, but this role can't touch this endpoint" (@PreAuthorize failing for
     * a real, authenticated user) surface as the same AccessDeniedException
     * from Spring Security's method security — there's no exception-type
     * distinction between the two. But the frontend needs one: api.ts only
     * treats a 401 as "try a silent refresh, then log out" (SessionExpiredError);
     * a 403 it just shows as a plain error message and does nothing else.
     * Sending 403 for an expired token meant a stale session showed a
     * permanent "Could not load records" instead of bouncing to /login. So
     * this checks the SecurityContext itself: no real (non-anonymous)
     * Authentication present means the client needs to re-authenticate (401,
     * triggers the refresh-then-logout flow); an actually-authenticated user
     * hitting a role check they fail gets the real 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest req) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasRealSession = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (!hasRealSession) {
            // The routine case — an expired/missing access token on a request
            // that arrived before (or instead of) the frontend's silent
            // refresh. INFO, not WARN: on its own this is expected traffic,
            // not a problem. What makes a burst of these worth noticing is
            // volume/timing, which is exactly what having them in the log at
            // all — with a timestamp — now makes visible.
            log.info("401 {} {} — no valid session (expired/missing/invalid token)", req.getMethod(), req.getRequestURI());
            return build(HttpStatus.UNAUTHORIZED, "Your session has expired. Please sign in again.", req, null);
        }
        // A real, authenticated user hit a role check they fail — worth a
        // closer look than a routine 401 (could be a frontend showing an
        // action it shouldn't, or someone probing).
        log.warn("403 {} {} [{}] — authenticated but not permitted", req.getMethod(), req.getRequestURI(), auth.getName());
        return build(HttpStatus.FORBIDDEN, "You don't have permission to do that.", req, null);
    }

    @ExceptionHandler({InvalidCredentialsException.class, BadCredentialsException.class})
    public ResponseEntity<ApiError> handleInvalidCredentials(RuntimeException ex, HttpServletRequest req) {
        log.warn("401 {} {} — invalid credentials: {}", req.getMethod(), req.getRequestURI(), ex.getMessage());
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ApiError> handleLocked(AccountLockedException ex, HttpServletRequest req) {
        log.warn("423 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.LOCKED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(OnLeaveException.class)
    public ResponseEntity<ApiError> handleOnLeave(OnLeaveException ex, HttpServletRequest req) {
        log.info("423 {} {} [{}]: {}", req.getMethod(), req.getRequestURI(), currentUsername(), ex.getMessage());
        return build(HttpStatus.LOCKED, ex.getMessage(), req, null);
    }

    @ExceptionHandler(PasswordChangeRequiredException.class)
    public ResponseEntity<ApiError> handlePasswordChangeRequired(PasswordChangeRequiredException ex, HttpServletRequest req) {
        log.debug("403 {} {} [{}] — password change required", req.getMethod(), req.getRequestURI(), currentUsername());
        ApiError body = new ApiError(HttpStatus.FORBIDDEN.value(), "PASSWORD_CHANGE_REQUIRED", ex.getMessage(), req.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .toList();
        log.debug("400 {} {} [{}] — validation failed: {}", req.getMethod(), req.getRequestURI(), currentUsername(), details);
        return build(HttpStatus.BAD_REQUEST, "Validation failed.", req, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest req) {
        // This used to say "logged server-side by the container/logging
        // framework" — it wasn't. This IS the last place anything could log
        // it, since this handler catches Exception.class before it reaches
        // anything else. Full stack trace, ERROR, with who/what/where —
        // this is the one log line every future "500, please investigate"
        // report should start from.
        log.error("500 {} {} [{}] — unhandled exception", req.getMethod(), req.getRequestURI(), currentUsername(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again.", req, null);
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest req, List<String> details) {
        ApiError body = new ApiError(status.value(), status.getReasonPhrase(), message, req.getRequestURI(), details);
        return ResponseEntity.status(status).body(body);
    }
}

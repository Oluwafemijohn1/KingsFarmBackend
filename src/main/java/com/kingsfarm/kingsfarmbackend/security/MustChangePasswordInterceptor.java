package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.common.exception.PasswordChangeRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Blocks every authenticated endpoint except the auth endpoints themselves
 * until a user with a fresh/reset password has changed it. Implemented as a
 * HandlerInterceptor (not a Filter) specifically so the exception it throws
 * propagates into GlobalExceptionHandler — exceptions thrown from a raw
 * Filter run outside DispatcherServlet's exception-handling machinery and
 * would otherwise produce a raw container error page instead of our
 * standard ApiError JSON body.
 * <p>
 * The whole {@code /api/v1/auth/*} family is exempt, not just change-password
 * and logout — a stale access-token cookie (still carrying the pre-change
 * mustChangePassword claim; JWT claims are a snapshot from issuance) gets
 * sent on every request regardless of which endpoint it's calling, including
 * a fresh {@code /login} attempt or an {@code /refresh} call. Blocking those
 * too would trap the caller: they can never obtain the new, correctly-
 * claimed token that would clear this in the first place. Originally only
 * change-password/logout were exempted, which reproduced exactly that trap —
 * a stale cookie made {@code /login} itself throw this same exception.
 */
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private static final String AUTH_PATH_PREFIX = "/api/v1/auth/";

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (request.getRequestURI().startsWith(AUTH_PATH_PREFIX)) {
            return true;
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedPrincipal principal
                && principal.mustChangePassword()) {
            throw new PasswordChangeRequiredException();
        }
        return true;
    }
}

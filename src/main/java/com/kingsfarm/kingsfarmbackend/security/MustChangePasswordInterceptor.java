package com.kingsfarm.kingsfarmbackend.security;

import com.kingsfarm.kingsfarmbackend.common.exception.PasswordChangeRequiredException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Blocks every authenticated endpoint except a small allow-list until a user
 * with a fresh/reset password has changed it. Implemented as a
 * HandlerInterceptor (not a Filter) specifically so the exception it throws
 * propagates into GlobalExceptionHandler — exceptions thrown from a raw
 * Filter run outside DispatcherServlet's exception-handling machinery and
 * would otherwise produce a raw container error page instead of our
 * standard ApiError JSON body.
 */
public class MustChangePasswordInterceptor implements HandlerInterceptor {

    private static final Set<String> ALLOWED_PATHS = Set.of(
            "/api/v1/auth/change-password",
            "/api/v1/auth/logout"
    );

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        if (ALLOWED_PATHS.contains(request.getRequestURI())) {
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

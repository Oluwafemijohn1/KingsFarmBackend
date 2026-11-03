package com.kingsfarm.kingsfarmbackend.rollover;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

/** Guarantees the day's rollover has run before any request is handled (no-op after the first call each day). */
public class DailyRolloverInterceptor implements HandlerInterceptor {

    private final DailyRolloverService service;

    public DailyRolloverInterceptor(DailyRolloverService service) {
        this.service = service;
    }

    @Override
    public boolean preHandle(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull Object handler) {
        service.ensureRolled();
        return true;
    }
}

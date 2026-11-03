package com.kingsfarm.kingsfarmbackend.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final com.kingsfarm.kingsfarmbackend.rollover.DailyRolloverService rolloverService;

    public WebMvcConfig(@org.springframework.context.annotation.Lazy com.kingsfarm.kingsfarmbackend.rollover.DailyRolloverService rolloverService) {
        this.rolloverService = rolloverService;
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(new com.kingsfarm.kingsfarmbackend.rollover.DailyRolloverInterceptor(rolloverService));
        registry.addInterceptor(new MustChangePasswordInterceptor());
    }
}

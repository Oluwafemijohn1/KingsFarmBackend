package com.kingsfarm.kingsfarmbackend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/** Binds the {@code app.security.*} block in application.yaml — see the comments there for what each value means. */
@Component
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    private String apiKey;

    @NestedConfigurationProperty
    private Jwt jwt = new Jwt();

    /** Whether the two auth cookies (BACKEND_PLAN.md §11 decision #2) get the Secure attribute — see application.yaml's comment. */
    private boolean cookieSecure = true;

    /**
     * Origins the frontend is served from — read into {@link SecurityConfig}'s
     * CORS filter. Comma-separated in the env var (Spring's relaxed binding
     * splits it into this list automatically); the Vite dev-server default
     * below is only a local-dev convenience, same as every other value here.
     */
    private List<String> corsAllowedOrigins = List.of();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public boolean isCookieSecure() {
        return cookieSecure;
    }

    public void setCookieSecure(boolean cookieSecure) {
        this.cookieSecure = cookieSecure;
    }

    public static class Jwt {
        private String secret;
        private int accessTokenMinutes = 30;
        private int refreshTokenDays = 7;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getAccessTokenMinutes() {
            return accessTokenMinutes;
        }

        public void setAccessTokenMinutes(int accessTokenMinutes) {
            this.accessTokenMinutes = accessTokenMinutes;
        }

        public int getRefreshTokenDays() {
            return refreshTokenDays;
        }

        public void setRefreshTokenDays(int refreshTokenDays) {
            this.refreshTokenDays = refreshTokenDays;
        }
    }
}

package com.kingsfarm.kingsfarmbackend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kingsfarm.kingsfarmbackend.common.ApiError;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * The outermost gate: every single request — authenticated or not — must
 * carry the shared {@code X-API-Key} header, checked here before Spring
 * Security, JWT parsing, or the database are ever touched. This is what
 * keeps a random client off the API entirely, separate from (and in
 * addition to) per-user authentication.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-API-Key";

    private final AppSecurityProperties properties;
    private final ObjectMapper objectMapper;

    public ApiKeyFilter(AppSecurityProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        String expected = properties.getApiKey();
        if (expected == null || expected.isBlank() || provided == null || !constantTimeEquals(expected, provided)) {
            writeUnauthorized(request, response);
            return;
        }
        chain.doFilter(request, response);
    }

    // Avoids leaking timing information about how much of the key matched.
    private boolean constantTimeEquals(String a, String b) {
        return java.security.MessageDigest.isEqual(
                a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                b.getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError body = new ApiError(HttpStatus.UNAUTHORIZED.value(), "Unauthorized", "Missing or invalid API key.", request.getRequestURI());
        objectMapper.writeValue(response.getWriter(), body);
    }
}

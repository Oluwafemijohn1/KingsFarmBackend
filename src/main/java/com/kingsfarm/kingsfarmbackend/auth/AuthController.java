package com.kingsfarm.kingsfarmbackend.auth;

import com.kingsfarm.kingsfarmbackend.auth.dto.*;
import com.kingsfarm.kingsfarmbackend.common.exception.InvalidCredentialsException;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Every endpoint here that issues or rotates tokens (login/refresh/logout)
 * sets/clears the two httpOnly cookies via {@link AuthCookies} rather than
 * handing the raw tokens back in the JSON body — see LoginResponse's javadoc
 * and BACKEND_PLAN.md §11 decision #2. login's JSON body still carries the
 * non-secret account info (userId/username/role/etc.) the frontend needs to
 * render immediately after signing in.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final AuthCookies authCookies;

    public AuthController(AuthService authService, AuthCookies authCookies) {
        this.authService = authService;
        this.authCookies = authCookies;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        LoginResponse response = authService.login(request, httpRequest.getRemoteAddr());
        authCookies.setAuthCookies(httpResponse, response.accessToken(), response.refreshToken());
        return response;
    }

    /** No request body — the refresh token is read straight off the httpOnly cookie, never handled by JS. */
    @PostMapping("/refresh")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void refresh(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawRefreshToken = AuthCookies.readCookie(httpRequest, AuthCookies.REFRESH_COOKIE);
        if (rawRefreshToken == null) {
            throw new InvalidCredentialsException("Invalid or expired refresh token.");
        }
        TokenResponse tokens = authService.refresh(rawRefreshToken);
        authCookies.setAuthCookies(httpResponse, tokens.accessToken(), tokens.refreshToken());
    }

    /** No request body, same reasoning as refresh — also always clears both cookies, even if no refresh cookie was present to revoke. */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
        String rawRefreshToken = AuthCookies.readCookie(httpRequest, AuthCookies.REFRESH_COOKIE);
        if (rawRefreshToken != null) {
            authService.logout(rawRefreshToken, httpRequest.getRemoteAddr());
        }
        authCookies.clearAuthCookies(httpResponse);
    }

    @PostMapping("/change-password")
    public void changePassword(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                @Valid @RequestBody ChangePasswordRequest request) {
        authService.changePassword(principal.userId(), request);
    }

    /** Session-bootstrap check — see AuthService.currentSession's javadoc. Requires a valid access-token cookie; Spring Security returns 401 on its own otherwise. */
    @GetMapping("/me")
    public LoginResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return authService.currentSession(principal.userId());
    }
}

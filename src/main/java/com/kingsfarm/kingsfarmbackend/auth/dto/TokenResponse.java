package com.kingsfarm.kingsfarmbackend.auth.dto;

/** Returned by /refresh — both tokens are rotated together, see AuthService.refresh. */
public record TokenResponse(
        String accessToken,
        String refreshToken
) {
}

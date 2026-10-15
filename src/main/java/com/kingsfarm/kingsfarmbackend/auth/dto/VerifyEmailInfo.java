package com.kingsfarm.kingsfarmbackend.auth.dto;

public record VerifyEmailInfo(String email, String fullName, boolean passwordReset, int passwordMinLength) {
}

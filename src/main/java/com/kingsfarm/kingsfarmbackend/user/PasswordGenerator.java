package com.kingsfarm.kingsfarmbackend.user;

import java.security.SecureRandom;

/**
 * Generates the one-time default password an Administrator gets back when
 * creating a user (BACKEND_PLAN.md §11, decision #4 — never stored or
 * retrievable in plain text after the create-user response). Mixes letters,
 * digits, and a couple of symbols so it clears any reasonable
 * passwordMinLength/complexity policy on first login.
 */
final class PasswordGenerator {

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%";
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordGenerator() {
    }

    static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }
}

package com.kingsfarm.kingsfarmbackend.common.exception;

/** Wrong username/password, inactive account, or an account currently on relief leave. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

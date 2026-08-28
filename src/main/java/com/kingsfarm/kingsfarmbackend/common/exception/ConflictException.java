package com.kingsfarm.kingsfarmbackend.common.exception;

/** e.g. username already taken. */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}

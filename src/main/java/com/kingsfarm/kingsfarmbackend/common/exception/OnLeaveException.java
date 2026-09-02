package com.kingsfarm.kingsfarmbackend.common.exception;

/** Login rejected because an active ReliefGrant currently names this account as the on-leave party. */
public class OnLeaveException extends RuntimeException {
    public OnLeaveException(String message) {
        super(message);
    }
}

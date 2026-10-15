package com.kingsfarm.kingsfarmbackend.wholeegg;

/**
 * Payment Auditing status for a transfer-paid {@link WeSaleTransaction}.
 * Every transaction defaults to {@code UNREVIEWED} until the Administrator
 * checks the actual bank account and marks it {@code CONFIRMED} (money has
 * entered) or {@code NOT_CONFIRMED} (it hasn't, or something about it
 * doesn't match — see {@code verificationRemark}). Cash-only transactions
 * are never surfaced in the auditing UI at all, so this field is only ever
 * meaningful for a transaction whose paymentMethods contains TRANSFER.
 */
public enum VerificationStatus {
    UNREVIEWED, CONFIRMED, NOT_CONFIRMED
}

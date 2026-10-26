package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import jakarta.validation.constraints.Min;

/** One bank + amount of a (possibly multi-bank) transfer. */
public record BankTransferRequest(String bank, @Min(0) long amount) {
}

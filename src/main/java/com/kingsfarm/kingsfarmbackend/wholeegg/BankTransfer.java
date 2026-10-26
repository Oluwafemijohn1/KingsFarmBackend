package com.kingsfarm.kingsfarmbackend.wholeegg;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One bank a customer transferred to for a single transaction, and how much went to it. A transaction can have several. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BankTransfer {

    @Column(name = "bank", nullable = false, length = 64)
    private String bank;

    @Column(name = "amount", nullable = false)
    private long amount;
}

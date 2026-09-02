package com.kingsfarm.kingsfarmbackend.crackegg;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A Good Crack sale — mirrors store.tsx's CESaleTxn exactly, including the
 * one deliberate difference from Whole Egg: {@code credit}/{@code advance}
 * are hand-typed by staff here, never system-derived (see CESaleTxn's
 * comment: "Manually entered by staff — not calculated by the system").
 * {@code customer} stays free text — Crack Egg never got the customer
 * directory treatment Whole Egg did (BACKEND_PLAN.md §11, decided).
 */
@Entity
@Table(name = "gc_sale_transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GcSaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String customer;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long price;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "gc_sale_transaction_payment_methods", joinColumns = @JoinColumn(name = "transaction_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 16)
    @Builder.Default
    private Set<PaymentMethod> paymentMethods = new HashSet<>();

    @Column(length = 64)
    private String bank;

    @Column(name = "cash_amount", nullable = false)
    @Builder.Default
    private long cashAmount = 0;

    @Column(name = "transfer_amount", nullable = false)
    @Builder.Default
    private long transferAmount = 0;

    @Column(name = "amount_paid", nullable = false)
    @Builder.Default
    private long amountPaid = 0;

    /** Hand-typed by staff, not derived — see class javadoc. */
    @Column(nullable = false)
    @Builder.Default
    private long credit = 0;

    @Column(nullable = false)
    @Builder.Default
    private long advance = 0;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
    }
}

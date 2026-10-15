package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.Set;

/**
 * One row per sale, debt payment, or opening-balance seed (see
 * {@link WeSaleTxnType}) — all three share this shape because a
 * payment/opening carries zero qty/prices, matching store.tsx's WESaleTxn
 * exactly. {@code occurredAt} is the single canonical timestamp;
 * {@code txnYear} is a derived, indexed column purely so year-restricted
 * history queries (Administrator sees every year, a manager sees only the
 * current year) don't need a function-based index or a full table scan.
 */
@Entity
@Table(name = "we_sale_transactions", indexes = {
        @Index(name = "idx_we_txn_customer_time", columnList = "customer_id, occurred_at"),
        @Index(name = "idx_we_txn_year", columnList = "txn_year")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeSaleTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    /** Denormalized snapshot so historical rows still render correctly even if the customer's name/state changes later. */
    @Column(name = "customer_name_snapshot", nullable = false, length = 201)
    private String customerNameSnapshot;

    @Column(nullable = false, length = 64)
    private String state;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeSaleTxnType type;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "txn_year", nullable = false)
    private int txnYear;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "we_sale_transaction_payment_methods", joinColumns = @JoinColumn(name = "transaction_id"))
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

    /**
     * Never hand-typed — always derived from amountPaid vs. what's owed
     * (this transaction's own total plus whatever balance carried in).
     * double because totalDue can be fractional when a fractional crate
     * quantity is sold (qty × price per crate), and that precision must be
     * retained in the running balance rather than rounded away.
     */
    @Column(nullable = false)
    @Builder.Default
    private double credit = 0;

    @Column(nullable = false)
    @Builder.Default
    private double advance = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    /**
     * Payment Auditing (admin-only, Whole Egg only — see VerificationStatus).
     * Meaningful only when paymentMethods contains TRANSFER; a cash-only
     * transaction just sits at the UNREVIEWED default forever, unsurfaced.
     * {@code columnDefinition} spells out an explicit SQL DEFAULT — belt and
     * suspenders for {@code ddl-auto: update}: without it, the ALTER TABLE
     * that adds this NOT NULL column to an already-populated table has no
     * DEFAULT clause, and depending on the DB's SQL mode that can silently
     * backfill existing rows with the type's implicit default (e.g. an
     * empty string) rather than a real enum constant.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "verification_status", nullable = false, length = 16, columnDefinition = "VARCHAR(16) DEFAULT 'UNREVIEWED'")
    @Builder.Default
    private VerificationStatus verificationStatus = VerificationStatus.UNREVIEWED;

    /** Admin's free-text note, e.g. "System says Zenith, customer actually transferred to FCMB." */
    @Column(name = "verification_remark", length = 500)
    private String verificationRemark;

    @Column(name = "verified_by", length = 64)
    private String verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) {
            occurredAt = Instant.now();
        }
        txnYear = occurredAt.atZone(ZoneId.systemDefault()).getYear();
    }
}

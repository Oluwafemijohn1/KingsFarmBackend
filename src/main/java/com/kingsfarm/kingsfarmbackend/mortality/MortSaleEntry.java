package com.kingsfarm.kingsfarmbackend.mortality;

import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * A Good/Dry/Runt sale — mirrors {@code MortSaleEntry} in MortalityView.tsx.
 * Like Crack Egg's {@code GcSaleTransaction} (and unlike Whole Egg),
 * credit/advance are hand-typed by staff and persisted as-is, never
 * server-derived — {@code addSale()} in the frontend takes
 * {@code newSaleCredit}/{@code newSaleAdvance} straight from their own
 * NumInputs. Customer stays free text — Mortality never got the customer
 * directory treatment Whole Egg did, same as Crack Egg.
 */
@Entity
@Table(name = "mort_sale_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortSaleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MortCat category;

    @Column(nullable = false)
    private int qty;

    @Column(nullable = false)
    private long price;

    @Column(nullable = false, length = 255)
    private String customer;

    @Column(nullable = false, length = 64)
    private String state;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "mort_sale_entry_payment_methods", joinColumns = @JoinColumn(name = "transaction_id"))
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

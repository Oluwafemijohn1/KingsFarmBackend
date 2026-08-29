package com.kingsfarm.kingsfarmbackend.wholeegg;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Whole Egg's customer directory — Crack Egg deliberately never got this
 * treatment (its customer field stays free text, per an explicit decision
 * in BACKEND_PLAN.md §11). A customer's credit/advance standing is never
 * stored here; it's derived from their most recent WeSaleTransaction (see
 * WholeEggService.customerBalance) — a running-balance model, not a sum.
 */
@Entity
@Table(name = "we_customers")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false, length = 32)
    private String phone;

    @Column(nullable = false, length = 64)
    private String state;

    @Column(length = 100)
    private String lga;

    @Column(length = 255)
    private String street;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public String fullName() {
        return firstName + " " + lastName;
    }
}

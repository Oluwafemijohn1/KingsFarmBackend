package com.kingsfarm.kingsfarmbackend.mortality;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * One row per day — the Catfish Feed Transfer (Green only) and Disposal
 * (PM/Reject only) quantities on the Catfish & Disposal tab. Both figures
 * share a single {@code SaveLockBar} in the frontend (one "Save Catfish &
 * Disposal" action locks both together), so they're modeled as one row
 * rather than two, with the same whole-day/whole-record lock rule as
 * {@code ProductionDayState}/{@code BirdPenRecord}: entryDate == today means
 * editable, otherwise permanently locked. No carry-forward — a new day
 * starts both quantities at zero, same as {@link MortPenEntry}.
 */
@Entity
@Table(name = "mort_catfish_disposal_state", uniqueConstraints = @UniqueConstraint(columnNames = "entry_date"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MortCatfishDisposalState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "catfish_qty", nullable = false)
    @Builder.Default
    private int catfishQty = 0;

    @Column(name = "disposal_qty", nullable = false)
    @Builder.Default
    private int disposalQty = 0;

    @Column(name = "entered_by", length = 64)
    private String enteredBy;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;
}

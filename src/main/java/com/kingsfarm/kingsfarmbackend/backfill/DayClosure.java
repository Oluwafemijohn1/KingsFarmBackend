package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A manager's statement that one module's records for one day are complete.
 * Days must be closed in order, which is what stops a later day being entered
 * before an earlier one (and so keeps every carried-forward figure correct).
 */
@Entity
@Table(name = "day_closures", uniqueConstraints = @UniqueConstraint(columnNames = {"module", "closed_date"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DayClosure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Mod module;

    @Column(name = "closed_date", nullable = false)
    private LocalDate closedDate;

    @Column(name = "closed_by", length = 64)
    private String closedBy;

    @Column(name = "closed_at", nullable = false)
    private Instant closedAt;

    @PrePersist
    void onCreate() {
        if (closedAt == null) {
            closedAt = Instant.now();
        }
    }
}

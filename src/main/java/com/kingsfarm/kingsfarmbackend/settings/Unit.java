package com.kingsfarm.kingsfarmbackend.settings;

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

/**
 * The farm-wide Units of Measurement catalog (Admin → System Configuration).
 * Unlike Pens/Feed Types/Ingredients — which are owned and edited by the
 * module manager who actually uses them — this one has no other natural
 * single owner: Administrator manages it from System Configuration
 * (add + remove), and Feed Mill Manager can also add a unit — but not
 * remove one — inline from the ingredient unit picker if something they
 * need isn't in the list yet (see UnitController's javadoc for the exact
 * per-role add/remove/read shape). No soft-deactivate, mirroring
 * {@code FeedType}. Seeded with kg/g/ton on first startup by
 * {@link UnitBootstrapRunner}.
 *
 * Consumed by Feed Mill's ingredient unit picker (FeedMillView.tsx fetches
 * GET /api/v1/admin/units to populate that dropdown's options). Its
 * kg/g/ton conversion math ({@code toGrams()}/{@code fromGrams()}/
 * {@code convertUnits()}) only understands those three weight units by
 * name; any other unit added here (e.g. "Bag", "Litre") is still perfectly
 * usable — ingredients measured in it just don't get automatic inter-unit
 * conversion, the same graceful pass-through the frontend already applies
 * to any non-kg/g/ton unit today.
 */
@Entity
@Table(name = "units", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Unit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String name;
}

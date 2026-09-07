package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "we_category_values", uniqueConstraints = @UniqueConstraint(columnNames = {"kind", "category"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WeCategoryValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WeCategoryValueKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private CatKey category;

    /**
     * double covers both fractional whole-crate quantities (OPENING/
     * SALES_CRACK/GIFT — 0.5 crate = 15 eggs, etc., per the Crate Quantity &
     * Conversion spec) and whole-Naira PRICE without needing two column
     * types; PRICE is always entered as a whole number via the money-
     * formatted input, so widening it here causes no behavior change there.
     */
    @Column(nullable = false)
    @Builder.Default
    private double value = 0;
}

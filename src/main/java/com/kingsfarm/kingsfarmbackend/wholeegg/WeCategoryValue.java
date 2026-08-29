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

    /** long covers both whole-crate quantities and Naira prices without needing two column types. */
    @Column(nullable = false)
    @Builder.Default
    private long value = 0;
}

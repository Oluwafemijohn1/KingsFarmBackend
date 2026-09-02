package com.kingsfarm.kingsfarmbackend.feedmill;

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
 * One row per fish feed type — always exactly the three fixed types in
 * {@link FeedMillService#FISH_FEED_TYPES} (Fish Starter/Grower/Finisher);
 * the frontend's {@code fishFeeds} state never grows or shrinks, unlike
 * ingredients/feed types. Same always-current-no-day-dimension pattern as
 * {@link FeedIngredient}. Opening is opening-stock-locked (module=FEED_MILL,
 * scope="fish:" + type, matching {@code scope={`fish:${f.type}`}} in
 * FeedMillView.tsx exactly). Added is never directly editable — only
 * auto-incremented when a fish-feed-type production run completes
 * ("auto transfer" in the frontend's own label). Collected is never
 * directly editable either — only accumulated via Fish Feed Collection
 * records.
 */
@Entity
@Table(name = "fish_feed_stock", uniqueConstraints = @UniqueConstraint(columnNames = "type"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FishFeedStock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String type;

    @Column(nullable = false)
    @Builder.Default
    private double opening = 0;

    @Column(nullable = false)
    @Builder.Default
    private double added = 0;

    @Column(nullable = false)
    @Builder.Default
    private double collected = 0;

    public double closing() {
        return opening + added - collected;
    }
}

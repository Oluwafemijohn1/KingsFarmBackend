package com.kingsfarm.kingsfarmbackend.settings;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Seeds the Units of Measurement catalog with kg/g/ton on first startup —
 * mirrors {@link com.kingsfarm.kingsfarmbackend.user.AdminBootstrapRunner}'s
 * shape (no-op once the table has any rows). Without this, a fresh database
 * would start with an empty Units catalog and Feed Mill's ingredient unit
 * picker (which now sources its options from GET /api/v1/admin/units — see
 * FeedMillView.tsx) would have nothing to offer. These three specifically
 * because they're the only units {@code convertUnits()}/{@code toGrams()}/
 * {@code fromGrams()} on the frontend actually know how to convert between;
 * an Administrator can still add further units (e.g. "Bag", "Litre") — they
 * just won't support automatic inter-unit conversion, same as any ingredient
 * whose unit isn't kg/g/ton today.
 */
@Component
public class UnitBootstrapRunner implements CommandLineRunner {

    private final UnitRepository repository;

    public UnitBootstrapRunner(UnitRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        for (String name : List.of("kg", "g", "ton")) {
            repository.save(Unit.builder().name(name).build());
        }
    }
}

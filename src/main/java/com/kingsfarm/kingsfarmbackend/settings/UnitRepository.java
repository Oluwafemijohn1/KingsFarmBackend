package com.kingsfarm.kingsfarmbackend.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UnitRepository extends JpaRepository<Unit, Long> {
    List<Unit> findAllByOrderByIdAsc();
    boolean existsByNameIgnoreCase(String name);
}

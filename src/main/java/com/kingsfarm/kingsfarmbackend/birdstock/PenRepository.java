package com.kingsfarm.kingsfarmbackend.birdstock;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PenRepository extends JpaRepository<Pen, Long> {
    List<Pen> findAllByActiveTrueOrderByNameAsc();
    List<Pen> findAllByOrderByNameAsc();
    boolean existsByName(String name);
    Optional<Pen> findByName(String name);
}

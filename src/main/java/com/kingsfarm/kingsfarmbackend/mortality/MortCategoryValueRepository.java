package com.kingsfarm.kingsfarmbackend.mortality;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MortCategoryValueRepository extends JpaRepository<MortCategoryValue, Long> {
    Optional<MortCategoryValue> findByCategory(MortCat category);
}

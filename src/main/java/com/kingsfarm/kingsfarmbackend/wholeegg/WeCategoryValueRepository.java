package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WeCategoryValueRepository extends JpaRepository<WeCategoryValue, Long> {
    Optional<WeCategoryValue> findByKindAndCategory(WeCategoryValueKind kind, CatKey category);
    List<WeCategoryValue> findAllByKind(WeCategoryValueKind kind);
}

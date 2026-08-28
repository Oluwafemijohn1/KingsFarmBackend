package com.kingsfarm.kingsfarmbackend.openingstock;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OpeningStockStateRepository extends JpaRepository<OpeningStockState, Long> {
    Optional<OpeningStockState> findByModuleAndScope(Mod module, String scope);
}

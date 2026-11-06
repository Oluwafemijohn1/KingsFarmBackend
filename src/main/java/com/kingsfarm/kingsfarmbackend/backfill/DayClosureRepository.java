package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.common.Mod;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DayClosureRepository extends JpaRepository<DayClosure, Long> {
    List<DayClosure> findAllByModuleAndClosedDateGreaterThanEqual(Mod module, LocalDate from);
    boolean existsByModuleAndClosedDate(Mod module, LocalDate date);
}

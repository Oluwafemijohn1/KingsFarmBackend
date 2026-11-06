package com.kingsfarm.kingsfarmbackend.backfill.dto;

import java.time.LocalDate;

/**
 * workingDay is the one day this module may currently be entered for (the first day not yet closed);
 * null when every day up to today is closed. daysBehind counts unclosed days before today.
 */
public record DayStatusResponse(LocalDate startDate, LocalDate today, LocalDate workingDay, int daysBehind, boolean todayClosed) {
}

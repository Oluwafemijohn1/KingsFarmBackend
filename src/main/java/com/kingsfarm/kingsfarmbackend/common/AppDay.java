package com.kingsfarm.kingsfarmbackend.common;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/** Day boundaries in the same zone LocalDate.now() uses, so "today" means the same thing everywhere. */
public final class AppDay {
    private AppDay() {}

    public static Instant start(LocalDate d) {
        return d.atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    /** Exclusive end of the day (start of the next day). */
    public static Instant end(LocalDate d) {
        return d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
    }
}

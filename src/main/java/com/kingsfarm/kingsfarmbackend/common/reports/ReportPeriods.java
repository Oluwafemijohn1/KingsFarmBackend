package com.kingsfarm.kingsfarmbackend.common.reports;

import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Shared helpers for every module's {@code /reports/daily} and
 * {@code /reports/monthly} endpoints — see BACKEND_PLAN.md §5.8. Every module
 * builds one row per calendar day (for a bounded date range, used by the
 * frontend's Week/custom-range views) or one row per trailing calendar month
 * (used by Quarter/Half-Year/Year), rather than a literal port of the
 * frontend's six hard-coded period buckets.
 */
public final class ReportPeriods {

    public static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DAY_LABEL = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH);
    private static final DateTimeFormatter MONTH_LABEL_WITH_YEAR = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private ReportPeriods() {
    }

    /** Every calendar day in [start, end], inclusive. Capped at 366 days so a fat-fingered range can't trigger an unbounded scan. */
    public static List<LocalDate> daysBetween(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw new BadRequestException("Both start and end dates are required.");
        }
        if (end.isBefore(start)) {
            throw new BadRequestException("End date cannot be before start date.");
        }
        if (ChronoUnit.DAYS.between(start, end) > 366) {
            throw new BadRequestException("Date range is too wide — please narrow it to a year or less.");
        }
        return start.datesUntil(end.plusDays(1)).toList();
    }

    /** The trailing {@code count} calendar months, oldest first, ending with the current month. */
    public static List<YearMonth> trailingMonths(int count) {
        if (count < 1 || count > 24) {
            throw new BadRequestException("months must be between 1 and 24.");
        }
        YearMonth current = YearMonth.now();
        return IntStream.rangeClosed(0, count - 1)
                .mapToObj(i -> current.minusMonths((long) count - 1 - i))
                .toList();
    }

    public static String dayLabel(LocalDate date) {
        return date.format(DAY_LABEL);
    }

    public static String monthLabel(YearMonth month) {
        return month.format(month.getYear() == YearMonth.now().getYear() ? MONTH_LABEL : MONTH_LABEL_WITH_YEAR);
    }

    /** Start-of-day (inclusive) as an Instant, for modules whose log entries are Instant-stamped rather than LocalDate-stamped. */
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(ZONE).toInstant();
    }

    /** Start of the following day (exclusive upper bound) as an Instant — pair with {@link #startOfDay} for a half-open {@code [start, end)} range. */
    public static Instant startOfNextDay(LocalDate date) {
        return date.plusDays(1).atStartOfDay(ZONE).toInstant();
    }
}

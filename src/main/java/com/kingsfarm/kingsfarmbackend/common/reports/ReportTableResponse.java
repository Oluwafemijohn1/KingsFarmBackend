package com.kingsfarm.kingsfarmbackend.common.reports;

import java.util.List;
import java.util.Map;

/**
 * Generic per-module Reports payload. Each row is a plain {@code LinkedHashMap}
 * keyed by the same column keys declared in {@link #columns()}, plus a
 * {@code "period"} entry holding the row's display label (a date, day name, or
 * month name depending on which endpoint produced it) — this is a deliberate
 * flattening of the frontend's {@code {period, [key]: value}[]} row shape so it
 * serializes with zero custom logic (plain String map keys, so none of the
 * enum-key Jackson ambiguity that CatKey-keyed maps elsewhere in this codebase
 * have to work around).
 *
 * See BACKEND_PLAN.md §5.8 for why this replaces a literal port of the
 * frontend's six-bucket (daily/weekly/monthly/quarterly/half-year/yearly)
 * ReportsPanel data shape: {@code monthly.tableRows} in the frontend is a
 * synthetic day-cycling hack that only exists because the demo had no real
 * per-day history, and every top-level stat card is already re-derivable from
 * these rows client-side (ReportsPanel already contains an
 * {@code aggregateStats} helper for exactly this, used today for custom date
 * ranges) — so the backend only needs to hand back real rows, not
 * pre-computed stats, and not a redundant "monthly" bucket that duplicates
 * what a client-side day-cycle over the weekly bucket already faked.
 */
public record ReportTableResponse(List<ReportColumn> columns, List<Map<String, Object>> rows) {
}

package com.kingsfarm.kingsfarmbackend.common.reports;

/** One column header for a {@link ReportTableResponse} — mirrors the frontend's {@code {key, label, mono}} shape exactly. */
public record ReportColumn(String key, String label, boolean mono) {
}

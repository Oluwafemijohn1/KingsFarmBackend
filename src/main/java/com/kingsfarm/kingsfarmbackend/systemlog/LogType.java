package com.kingsfarm.kingsfarmbackend.systemlog;

/** Matches AdminView's three log tabs (Access / Activity / Audit) — one table, discriminated by this column, instead of three static arrays. */
public enum LogType {
    ACCESS, ACTIVITY, AUDIT
}

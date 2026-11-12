package com.kingsfarm.kingsfarmbackend.wholeegg.dto;

import com.kingsfarm.kingsfarmbackend.common.CatKey;

import java.time.LocalDate;
import java.util.List;

/** One day's stock per size: opening, what came in, what went out, closing. */
public record DailyStockResponse(LocalDate day, List<Line> categories) {

    /** {@code crackAndGift} is crack use plus gifts together (a finished day only keeps the combined figure). */
    public record Line(CatKey category, double opening, double production, double sales, double crackAndGift, double closing) {
    }
}

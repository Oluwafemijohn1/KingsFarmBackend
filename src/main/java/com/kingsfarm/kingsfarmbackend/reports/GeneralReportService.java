package com.kingsfarm.kingsfarmbackend.reports;

import com.kingsfarm.kingsfarmbackend.common.reports.ReportColumn;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggService;
import com.kingsfarm.kingsfarmbackend.feedmill.FeedMillService;
import com.kingsfarm.kingsfarmbackend.mortality.MortalityService;
import com.kingsfarm.kingsfarmbackend.production.ProductionService;
import com.kingsfarm.kingsfarmbackend.wholeegg.WholeEggService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Backs GeneralReportView — the Managing Director's farm-wide summary
 * (BACKEND_PLAN.md §8). Structurally different from every other module's
 * Reports endpoints: one row per <b>module</b> for the selected date range,
 * not one row per day/month. Rather than re-deriving revenue/output totals
 * from scratch, this composes each module's own {@code dailyReport(start,
 * end)} — already real, already-tested aggregation — and sums the relevant
 * columns across its rows. Bird Stock/Production and Feed Mill have no
 * revenue concept (frontend shows "—" for both, matching the original
 * generalReportData), so their {@code revenue}/{@code share} stay "—" too,
 * rather than being invented.
 */
@Service
public class GeneralReportService {

    private static final List<ReportColumn> COLUMNS = List.of(
            new ReportColumn("revenue", "Revenue", false),
            new ReportColumn("sales", "Sales / Output", true),
            new ReportColumn("share", "% of Total Revenue", true)
    );

    private final ProductionService productionService;
    private final WholeEggService wholeEggService;
    private final CrackEggService crackEggService;
    private final MortalityService mortalityService;
    private final FeedMillService feedMillService;

    public GeneralReportService(ProductionService productionService,
                                 WholeEggService wholeEggService,
                                 CrackEggService crackEggService,
                                 MortalityService mortalityService,
                                 FeedMillService feedMillService) {
        this.productionService = productionService;
        this.wholeEggService = wholeEggService;
        this.crackEggService = crackEggService;
        this.mortalityService = mortalityService;
        this.feedMillService = feedMillService;
    }

    @Transactional(readOnly = true)
    public ReportTableResponse general(LocalDate start, LocalDate end) {
        List<Map<String, Object>> productionRows = productionService.dailyReport(start, end).rows();
        List<Map<String, Object>> wholeEggRows = wholeEggService.dailyReport(start, end).rows();
        List<Map<String, Object>> crackEggRows = crackEggService.dailyReport(start, end).rows();
        List<Map<String, Object>> mortalityRows = mortalityService.dailyReport(start, end).rows();
        List<Map<String, Object>> feedMillRows = feedMillService.dailyReport(start, end).rows();

        long productionCrates = sumLong(productionRows, "crates");
        long weRevenue = sumLong(wholeEggRows, "revenue");
        long weCrates = sumLong(wholeEggRows, "crates");
        long ceRevenue = sumLong(crackEggRows, "goodRevenue");
        long ceUnits = sumLong(crackEggRows, "goodSales");
        long mortRevenue = sumLong(mortalityRows, "salesRevenue");
        long mortBirds = sumLong(mortalityRows, "sales");
        double feedTons = sumDouble(feedMillRows, "produced");

        long totalRevenue = weRevenue + ceRevenue + mortRevenue;

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(moduleRow("Production (Bird Stock)", null, productionCrates + " crates produced", 0));
        rows.add(moduleRow("Whole Egg", weRevenue, weCrates + " crates", totalRevenue));
        rows.add(moduleRow("Crack Egg", ceRevenue, ceUnits + " units", totalRevenue));
        rows.add(moduleRow("Mortality", mortRevenue, mortBirds + " birds", totalRevenue));
        rows.add(moduleRow("Feed Mill", null, feedTons + " tons produced", 0));

        return new ReportTableResponse(COLUMNS, rows);
    }

    private Map<String, Object> moduleRow(String period, Long revenue, String salesLabel, long totalRevenueForShare) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("period", period);
        row.put("revenue", revenue == null ? "—" : revenue);
        row.put("sales", salesLabel);
        row.put("share", (revenue == null || totalRevenueForShare == 0) ? "—"
                : String.format(Locale.US, "%.1f%%", revenue * 100.0 / totalRevenueForShare));
        return row;
    }

    private static long sumLong(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToLong(r -> ((Number) r.get(key)).longValue()).sum();
    }

    private static double sumDouble(List<Map<String, Object>> rows, String key) {
        return rows.stream().mapToDouble(r -> ((Number) r.get(key)).doubleValue()).sum();
    }
}

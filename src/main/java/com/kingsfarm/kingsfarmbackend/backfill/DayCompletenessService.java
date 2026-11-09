package com.kingsfarm.kingsfarmbackend.backfill;

import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecordRepository;
import com.kingsfarm.kingsfarmbackend.common.AppDay;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggGiftLogRepository;
import com.kingsfarm.kingsfarmbackend.crackegg.GcSaleTransactionRepository;
import com.kingsfarm.kingsfarmbackend.feedmill.FeedCollectionLogEntryRepository;
import com.kingsfarm.kingsfarmbackend.feedmill.FeedProductionLogEntryRepository;
import com.kingsfarm.kingsfarmbackend.mortality.MortCat;
import com.kingsfarm.kingsfarmbackend.mortality.MortPenEntryRepository;
import com.kingsfarm.kingsfarmbackend.production.ProductionPenEntryRepository;
import com.kingsfarm.kingsfarmbackend.wholeegg.WeSaleTransactionRepository;
import com.kingsfarm.kingsfarmbackend.wholeegg.WeSaleTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * What each module is expected to have recorded for a day before that day can be closed.
 * Only applied to days in the past - today closes by itself when the next day starts.
 */
@Service
public class DayCompletenessService {

    private final FeedProductionLogEntryRepository feedProduction;
    private final FeedCollectionLogEntryRepository feedCollection;
    private final MortPenEntryRepository mortPens;
    private final ProductionPenEntryRepository productionPens;
    private final BirdPenRecordRepository birdRecords;
    private final WeSaleTransactionRepository weSales;
    private final GcSaleTransactionRepository gcSales;
    private final CrackEggGiftLogRepository gcGifts;

    public DayCompletenessService(FeedProductionLogEntryRepository feedProduction,
                                  FeedCollectionLogEntryRepository feedCollection,
                                  MortPenEntryRepository mortPens,
                                  ProductionPenEntryRepository productionPens,
                                  BirdPenRecordRepository birdRecords,
                                  WeSaleTransactionRepository weSales,
                                  GcSaleTransactionRepository gcSales,
                                  CrackEggGiftLogRepository gcGifts) {
        this.feedProduction = feedProduction;
        this.feedCollection = feedCollection;
        this.mortPens = mortPens;
        this.productionPens = productionPens;
        this.birdRecords = birdRecords;
        this.weSales = weSales;
        this.gcSales = gcSales;
        this.gcGifts = gcGifts;
    }

    /** Human-readable list of what is still missing for the day (empty = complete). */
    @Transactional(readOnly = true)
    public List<String> missing(Mod module, LocalDate day) {
        List<String> missing = new ArrayList<>();
        Instant start = AppDay.start(day);
        Instant end = AppDay.end(day);
        switch (module) {
            case FEED_MILL -> {
                if (feedProduction.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end).isEmpty()) {
                    missing.add("Feed milling (production) - no feed was recorded as milled");
                }
                if (feedCollection.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end).isEmpty()) {
                    missing.add("Fish feed collection - nothing was recorded as collected");
                }
            }
            case MORTALITY -> {
                int total = mortPens.findAllByEntryDate(day).stream()
                        .mapToInt(e -> {
                            int s = 0;
                            for (MortCat c : MortCat.values()) s += e.get(c);
                            return s;
                        }).sum();
                if (total <= 0) {
                    missing.add("Pen mortality - no dead birds were recorded in any pen");
                }
            }
            case PRODUCTION -> {
                if (productionPens.findAllByEntryDate(day).isEmpty()) {
                    missing.add("Pen production - no egg production was recorded");
                }
            }
            case BIRD_STOCK -> {
                if (birdRecords.findAllByEntryDate(day).isEmpty()) {
                    missing.add("Bird stock - no pen records were entered");
                }
            }
            case WHOLE_EGG -> {
                boolean any = weSales.findAllByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(WeSaleTxnType.SALE, start, end).size() > 0;
                if (!any) {
                    missing.add("Whole egg sales - no sale was recorded");
                }
            }
            case CRACK_EGG -> {
                boolean any = !gcSales.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end).isEmpty()
                        || !gcGifts.findAllByOccurredAtGreaterThanEqualAndOccurredAtLessThan(start, end).isEmpty();
                if (!any) {
                    missing.add("Crack egg - no sale or gift was recorded");
                }
            }
            default -> { }
        }
        return missing;
    }
}

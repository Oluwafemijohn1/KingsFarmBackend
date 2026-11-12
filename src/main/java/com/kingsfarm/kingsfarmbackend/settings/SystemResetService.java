package com.kingsfarm.kingsfarmbackend.settings;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.birdstock.BirdPenRecordRepository;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggGiftLogRepository;
import com.kingsfarm.kingsfarmbackend.crackegg.CrackEggStateRepository;
import com.kingsfarm.kingsfarmbackend.crackegg.GcSaleTransactionRepository;
import com.kingsfarm.kingsfarmbackend.feedmill.*;
import com.kingsfarm.kingsfarmbackend.mortality.*;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockRequestRepository;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockStateRepository;
import com.kingsfarm.kingsfarmbackend.production.ProductionDayStateRepository;
import com.kingsfarm.kingsfarmbackend.production.ProductionPenEntryRepository;
import com.kingsfarm.kingsfarmbackend.wholeegg.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Go live" reset: wipes every operational/test record so the farm starts
 * from zero, while keeping users (unless deleteOtherUsers, which removes every non-Administrator),
 * Security Settings, Units, Pens, Feed Types, Fish Feed catalog entries,
 * Ingredient names/minimums and Formulations, and the System Logs (so the
 * reset itself, and who ran it, stays on record).
 * Ingredient stock figures (opening/added/used) are zeroed, not deleted.
 * Delete order matters: children before the rows they reference.
 */
@Service
public class SystemResetService {

    private final BirdPenRecordRepository birdPenRecords;
    private final ProductionPenEntryRepository productionPenEntries;
    private final ProductionDayStateRepository productionDayStates;
    private final WeSaleLineItemRepository weLineItems;
    private final WeSaleTransactionRepository weTransactions;
    private final WeCategoryValueRepository weCategoryValues;
    private final CustomerRepository customers;
    private final CrackEggGiftLogRepository crackGiftLog;

    @org.springframework.beans.factory.annotation.Autowired
    private com.kingsfarm.kingsfarmbackend.wholeegg.WeAdjustmentLogRepository weAdjustmentLog;
    private final GcSaleTransactionRepository gcSales;
    private final CrackEggStateRepository crackState;
    private final MortPenEntryRepository mortPenEntries;
    private final MortCategoryValueRepository mortCategoryValues;
    private final MortSaleEntryRepository mortSales;
    private final MortGiftLogRepository mortGifts;
    private final MortCatfishDisposalStateRepository mortCatfish;
    private final FeedCollectionLogEntryRepository feedCollections;
    private final FeedProductionLogEntryRepository feedProductions;
    private final FeedFormulationHistoryItemRepository historyItems;
    private final FeedFormulationHistoryGroupRepository historyGroups;
    private final FishFeedStockRepository fishFeed;
    private final FeedIngredientRepository ingredients;
    private final OpeningStockRequestRepository openingRequests;
    private final OpeningStockStateRepository openingStates;
    private final com.kingsfarm.kingsfarmbackend.backfill.BackfillRequestRepository backfillRequests;
    private final com.kingsfarm.kingsfarmbackend.backfill.DayClosureService dayClosures;
    private final com.kingsfarm.kingsfarmbackend.rollover.DailyRolloverService rollover;
    private final com.kingsfarm.kingsfarmbackend.user.UserRepository users;
    private final com.kingsfarm.kingsfarmbackend.birdstock.BirdStockService birdStockService;
    private final com.kingsfarm.kingsfarmbackend.production.ProductionService productionService;
    private final com.kingsfarm.kingsfarmbackend.birdstock.PenRepository pens;
    private final FeedFormulationEntryRepository formulationEntries;
    private final FeedTypeRepository feedTypes;
    private final com.kingsfarm.kingsfarmbackend.systemlog.SystemLogRepository systemLogs;
    private final com.kingsfarm.kingsfarmbackend.user.RefreshTokenRepository refreshTokens;
    private final com.kingsfarm.kingsfarmbackend.user.AccountTokenRepository accountTokens;
    private final com.kingsfarm.kingsfarmbackend.relief.ReliefGrantRepository reliefGrants;

    public SystemResetService(BirdPenRecordRepository birdPenRecords,
                              ProductionPenEntryRepository productionPenEntries,
                              ProductionDayStateRepository productionDayStates,
                              WeSaleLineItemRepository weLineItems,
                              WeSaleTransactionRepository weTransactions,
                              WeCategoryValueRepository weCategoryValues,
                              CustomerRepository customers,
                              CrackEggGiftLogRepository crackGiftLog,
                              GcSaleTransactionRepository gcSales,
                              CrackEggStateRepository crackState,
                              MortPenEntryRepository mortPenEntries,
                              MortCategoryValueRepository mortCategoryValues,
                              MortSaleEntryRepository mortSales,
                              MortGiftLogRepository mortGifts,
                              MortCatfishDisposalStateRepository mortCatfish,
                              FeedCollectionLogEntryRepository feedCollections,
                              FeedProductionLogEntryRepository feedProductions,
                              FeedFormulationHistoryItemRepository historyItems,
                              FeedFormulationHistoryGroupRepository historyGroups,
                              FishFeedStockRepository fishFeed,
                              FeedIngredientRepository ingredients,
                              OpeningStockRequestRepository openingRequests,
                              OpeningStockStateRepository openingStates,
                              com.kingsfarm.kingsfarmbackend.backfill.BackfillRequestRepository backfillRequests,
                              com.kingsfarm.kingsfarmbackend.backfill.DayClosureService dayClosures,
                              @org.springframework.context.annotation.Lazy com.kingsfarm.kingsfarmbackend.rollover.DailyRolloverService rollover,
                              com.kingsfarm.kingsfarmbackend.user.UserRepository users,
                              @org.springframework.context.annotation.Lazy com.kingsfarm.kingsfarmbackend.birdstock.BirdStockService birdStockService,
                              @org.springframework.context.annotation.Lazy com.kingsfarm.kingsfarmbackend.production.ProductionService productionService,
                              com.kingsfarm.kingsfarmbackend.birdstock.PenRepository pens,
                              FeedFormulationEntryRepository formulationEntries,
                              FeedTypeRepository feedTypes,
                              com.kingsfarm.kingsfarmbackend.systemlog.SystemLogRepository systemLogs,
                              com.kingsfarm.kingsfarmbackend.user.RefreshTokenRepository refreshTokens,
                              com.kingsfarm.kingsfarmbackend.user.AccountTokenRepository accountTokens,
                              com.kingsfarm.kingsfarmbackend.relief.ReliefGrantRepository reliefGrants) {
        this.birdPenRecords = birdPenRecords;
        this.productionPenEntries = productionPenEntries;
        this.productionDayStates = productionDayStates;
        this.weLineItems = weLineItems;
        this.weTransactions = weTransactions;
        this.weCategoryValues = weCategoryValues;
        this.customers = customers;
        this.crackGiftLog = crackGiftLog;
        this.gcSales = gcSales;
        this.crackState = crackState;
        this.mortPenEntries = mortPenEntries;
        this.mortCategoryValues = mortCategoryValues;
        this.mortSales = mortSales;
        this.mortGifts = mortGifts;
        this.mortCatfish = mortCatfish;
        this.feedCollections = feedCollections;
        this.feedProductions = feedProductions;
        this.historyItems = historyItems;
        this.historyGroups = historyGroups;
        this.fishFeed = fishFeed;
        this.ingredients = ingredients;
        this.openingRequests = openingRequests;
        this.openingStates = openingStates;
        this.backfillRequests = backfillRequests;
        this.dayClosures = dayClosures;
        this.rollover = rollover;
        this.users = users;
        this.birdStockService = birdStockService;
        this.productionService = productionService;
        this.pens = pens;
        this.formulationEntries = formulationEntries;
        this.feedTypes = feedTypes;
        this.systemLogs = systemLogs;
        this.refreshTokens = refreshTokens;
        this.accountTokens = accountTokens;
        this.reliefGrants = reliefGrants;
    }

    /** Group keys the Administrator can tick; see the Reset dialog in AdminView. */
    public static final java.util.Set<String> ALL_RECORD_GROUPS = java.util.Set.of(
            "crack", "productionCrack", "wholeEgg", "production", "birdStock", "mortality", "feedMill", "locks", "closures", "activity");

    @Audited(module = Mod.ADMIN, action = "System Reset", detail = "'Selected records wiped: ' + #groups")
    @Transactional
    public void resetOperationalData(java.util.Set<String> groups) {
        boolean wholeEgg = groups.contains("wholeEgg");
        boolean production = groups.contains("production");
        boolean birdStock = groups.contains("birdStock");
        boolean mortality = groups.contains("mortality");
        boolean feedMill = groups.contains("feedMill");
        boolean locks = groups.contains("locks");
        boolean closures = groups.contains("closures");

        // Things other rows point at can only go when everything pointing at them goes too.
        if (groups.contains("pens") && !(production && birdStock && mortality)) {
            throw new com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException(
                    "Pens can only be deleted together with Production, Bird Stock and Mortality records.");
        }
        if (groups.contains("feedSetup") && !feedMill) {
            throw new com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException(
                    "The Feed Mill setup can only be deleted together with the Feed Mill records.");
        }
        if (groups.contains("users") && !(locks && closures)) {
            throw new com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException(
                    "Users can only be deleted together with the opening-stock requests and past-day permissions.");
        }

        if (groups.contains("crack")) {
            // Crack use and gifts, the Crack Egg module's records, and the crack figures kept on each Production day.
            weCategoryValues.findAll().forEach(v -> {
                if (v.getKind() == WeCategoryValueKind.SALES_CRACK || v.getKind() == WeCategoryValueKind.GIFT) {
                    v.setValue(0);
                    weCategoryValues.save(v);
                }
            });
            productionDayStates.findAll().forEach(d -> {
                d.setCrackGoodOpen(0);
                d.setCrackRoughOpen(0);
                d.setCrackGoodProd(0);
                d.setCrackRoughProd(0);
                d.setGoodClassify(0);
                d.setRoughClassify(0);
                d.setCrackFromWholeSnapshot(null);
                productionDayStates.save(d);
            });
            weAdjustmentLog.deleteAllInBatch();
            gcSales.deleteAllInBatch();
            crackGiftLog.deleteAllInBatch();
            crackState.findAll().forEach(c -> {
                c.setGcOpening(0);
                c.setRcOpening(0);
                c.setGcGiftQty(0);
                c.setGcGiftRecipient("");
                c.setGcGiftAuthorizer("");
                c.setRcFeedMill(0);
                crackState.save(c);
            });
        }

        if (wholeEgg) {
            weAdjustmentLog.deleteAllInBatch();
            weLineItems.deleteAllInBatch();
            weTransactions.deleteAllInBatch();
            weCategoryValues.deleteAllInBatch();
            customers.deleteAllInBatch();
        }

        if (mortality) {
            mortPenEntries.deleteAllInBatch();
            mortCategoryValues.deleteAllInBatch();
            mortSales.deleteAllInBatch();
            mortGifts.deleteAllInBatch();
            mortCatfish.deleteAllInBatch();
        }

        if (production) {
            productionPenEntries.deleteAllInBatch();
            productionDayStates.deleteAllInBatch();
        }
        if (birdStock) {
            birdPenRecords.deleteAllInBatch();
        }

        if (feedMill) {
            feedCollections.deleteAllInBatch();
            feedProductions.deleteAllInBatch();
            historyItems.deleteAllInBatch();
            historyGroups.deleteAllInBatch();
            fishFeed.deleteAllInBatch();
            if (groups.contains("feedSetup")) {
                formulationEntries.deleteAllInBatch();
                feedTypes.deleteAllInBatch();
                ingredients.deleteAllInBatch();
            } else {
                ingredients.findAll().forEach(i -> {
                    i.setOpening(0);
                    i.setAdded(0);
                    i.setUsed(0);
                    ingredients.save(i);
                });
            }
        }

        if (locks) {
            openingRequests.deleteAllInBatch();
            openingStates.deleteAllInBatch();
        }

        if (closures) {
            // Day-by-day bookkeeping starts over: no closed days, no past-day permissions, fresh day counter.
            backfillRequests.deleteAllInBatch();
            dayClosures.wipe();
            rollover.afterReset(dayClosures.startDate());
        }

        if (groups.contains("activity")) {
            // The dashboards' "Recent Activity" feeds read the Activity log; Access and Audit logs stay.
            systemLogs.deleteAllByLogType(com.kingsfarm.kingsfarmbackend.systemlog.LogType.ACTIVITY);
        }

        if (groups.contains("productionCrack") && !production) {
            // Production days kept their openings from when crack figures existed; bring them back in line so Crack Use shows 0.
            productionService.rebuildOpeningsWithoutCrack();
        }

        if (groups.contains("birdOpenings") && !birdStock) {
            birdStockService.rebuildOpenings();
        }

        if (groups.contains("pens")) {
            pens.deleteAllInBatch();
        }

        if (groups.contains("users")) {
            var admin = com.kingsfarm.kingsfarmbackend.user.Role.ADMINISTRATOR;
            reliefGrants.deleteAllInvolvingNon(admin);
            refreshTokens.deleteAllByUserRoleNot(admin);
            accountTokens.deleteAllByUserRoleNot(admin);
            users.deleteAllByRoleNot(admin);
        }
    }
}

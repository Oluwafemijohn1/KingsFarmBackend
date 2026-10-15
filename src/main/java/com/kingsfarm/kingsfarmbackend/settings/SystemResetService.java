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
 * from zero, while keeping users (and their sessions/relief grants),
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
                              OpeningStockStateRepository openingStates) {
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
    }

    @Audited(module = Mod.ADMIN, action = "System Reset", detail = "'All operational records wiped (users and configuration kept) - system started for real use'")
    @Transactional
    public void resetOperationalData() {
        weLineItems.deleteAllInBatch();
        weTransactions.deleteAllInBatch();
        weCategoryValues.deleteAllInBatch();
        customers.deleteAllInBatch();

        gcSales.deleteAllInBatch();
        crackGiftLog.deleteAllInBatch();
        crackState.deleteAllInBatch();

        mortPenEntries.deleteAllInBatch();
        mortCategoryValues.deleteAllInBatch();
        mortSales.deleteAllInBatch();
        mortGifts.deleteAllInBatch();
        mortCatfish.deleteAllInBatch();

        productionPenEntries.deleteAllInBatch();
        productionDayStates.deleteAllInBatch();
        birdPenRecords.deleteAllInBatch();

        feedCollections.deleteAllInBatch();
        feedProductions.deleteAllInBatch();
        historyItems.deleteAllInBatch();
        historyGroups.deleteAllInBatch();
        fishFeed.deleteAllInBatch();
        ingredients.findAll().forEach(i -> {
            i.setOpening(0);
            i.setAdded(0);
            i.setUsed(0);
            ingredients.save(i);
        });

        openingRequests.deleteAllInBatch();
        openingStates.deleteAllInBatch();
    }
}

package com.kingsfarm.kingsfarmbackend.crackegg;

import com.kingsfarm.kingsfarmbackend.audit.Audited;
import com.kingsfarm.kingsfarmbackend.common.Mod;
import com.kingsfarm.kingsfarmbackend.common.PaymentMethod;
import com.kingsfarm.kingsfarmbackend.common.exception.BadRequestException;
import com.kingsfarm.kingsfarmbackend.common.exception.ForbiddenException;
import com.kingsfarm.kingsfarmbackend.common.exception.NotFoundException;
import com.kingsfarm.kingsfarmbackend.crackegg.dto.*;
import com.kingsfarm.kingsfarmbackend.openingstock.OpeningStockLockService;
import com.kingsfarm.kingsfarmbackend.production.ProductionDayState;
import com.kingsfarm.kingsfarmbackend.production.ProductionService;
import com.kingsfarm.kingsfarmbackend.systemlog.LogType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

/**
 * Backs CrackEggView in full. Good Crack's Production/Received figures
 * (crackGoodProd/goodClassify) and Rough Crack's (crackRoughProd/
 * roughClassify) are read live from ProductionService's today's
 * ProductionDayState — the same cross-module read WholeEggService does for
 * catProdTotals. Good Crack's Gift qty and Sales total flow the other way
 * (Crack Egg → Production), so ProductionService injects this service
 * {@code @Lazy} for the same reason it does WholeEggService.
 */
@Service
public class CrackEggService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final CrackEggStateRepository stateRepository;
    private final GcSaleTransactionRepository saleRepository;
    private final CrackEggGiftLogRepository giftLogRepository;
    private final OpeningStockLockService lockService;
    private final ProductionService productionService;

    public CrackEggService(CrackEggStateRepository stateRepository,
                            GcSaleTransactionRepository saleRepository,
                            CrackEggGiftLogRepository giftLogRepository,
                            OpeningStockLockService lockService,
                            ProductionService productionService) {
        this.stateRepository = stateRepository;
        this.saleRepository = saleRepository;
        this.giftLogRepository = giftLogRepository;
        this.lockService = lockService;
        this.productionService = productionService;
    }

    @Transactional
    public CrackEggState state() {
        return stateRepository.findById(CrackEggState.SINGLETON_ID).orElseGet(() -> {
            CrackEggState state = new CrackEggState();
            return stateRepository.save(state);
        });
    }

    // ── Cross-module feed: Crack Egg → Production ───────────────────────────

    @Transactional(readOnly = true)
    public int goodGiftQty() {
        return state().getGcGiftQty();
    }

    @Transactional(readOnly = true)
    public int goodSalesQty() {
        return saleRepository.sumQty();
    }

    // ── Stock summary ────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public StockSummaryResponse stockSummary() {
        CrackEggState s = state();
        ProductionDayState prod = productionService.getTodayDayState();
        int gcProduced = prod.getCrackGoodProd();
        double gcReceived = prod.getGoodClassify();
        int rcProduced = prod.getCrackRoughProd();
        double rcReceived = prod.getRoughClassify();

        int gcTotalSales = saleRepository.sumQty();
        long gcRevenue = saleRepository.sumRevenue();
        int gcClosing = (int) Math.round(s.getGcOpening() + gcProduced + gcReceived - s.getGcGiftQty() - gcTotalSales);
        int rcClosing = (int) Math.round(s.getRcOpening() + rcProduced + rcReceived - s.getRcFeedMill());

        return new StockSummaryResponse(
                s.getGcOpening(), lockService.isLocked(Mod.CRACK_EGG, "good"), gcProduced, gcReceived,
                s.getGcSellingPrice(), s.getGcGiftQty(), s.getGcGiftRecipient(), s.getGcGiftAuthorizer(),
                gcTotalSales, gcRevenue, gcClosing,
                s.getRcOpening(), lockService.isLocked(Mod.CRACK_EGG, "rough"), rcProduced, rcReceived,
                s.getRcFeedMill(), rcClosing
        );
    }

    // ── Good Crack scalar fields ─────────────────────────────────────────

    @Audited(module = Mod.CRACK_EGG, action = "Update Opening Stock", detail = "'Good Crack -> ' + #request.value()")
    @Transactional
    public void setGcOpening(UpdateIntValueRequest request) {
        if (lockService.isLocked(Mod.CRACK_EGG, "good")) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        CrackEggState s = state();
        s.setGcOpening(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void lockGcOpening() {
        lockService.lock(Mod.CRACK_EGG, "good");
    }

    @Audited(module = Mod.CRACK_EGG, action = "Update Opening Stock", detail = "'Rough Crack -> ' + #request.value()")
    @Transactional
    public void setRcOpening(UpdateIntValueRequest request) {
        if (lockService.isLocked(Mod.CRACK_EGG, "rough")) {
            throw new ForbiddenException("Opening Stock is locked. Submit an Opening Stock request for the Administrator to approve.");
        }
        CrackEggState s = state();
        s.setRcOpening(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void lockRcOpening() {
        lockService.lock(Mod.CRACK_EGG, "rough");
    }

    @Transactional
    public void setGcSellingPrice(UpdateLongValueRequest request) {
        CrackEggState s = state();
        s.setGcSellingPrice(request.value());
        stateRepository.save(s);
    }

    // Gift qty/recipient/authorizer update live, no validation blocking — matches
    // the frontend, which only shows a visual warning (hasError) if gift qty
    // exceeds available stock but never disables saving.
    @Transactional
    public void setGcGiftQty(UpdateIntValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftQty(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void setGcGiftRecipient(UpdateTextValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftRecipient(request.value());
        stateRepository.save(s);
    }

    @Transactional
    public void setGcGiftAuthorizer(UpdateTextValueRequest request) {
        CrackEggState s = state();
        s.setGcGiftAuthorizer(request.value());
        stateRepository.save(s);
    }

    /** Unlike Gift, this IS hard-validated — the frontend disables its Save button once usage exceeds available Rough Crack stock. */
    @Audited(module = Mod.CRACK_EGG, action = "Save Feed Mill Usage", detail = "'Rough Crack -> ' + #request.value()")
    @Transactional
    public void setRcFeedMill(UpdateIntValueRequest request) {
        CrackEggState s = state();
        ProductionDayState prod = productionService.getTodayDayState();
        int available = s.getRcOpening() + prod.getCrackRoughProd() + (int) Math.round(prod.getRoughClassify());
        if (request.value() > available) {
            throw new BadRequestException("Cannot exceed available Rough Crack stock (" + available + ").");
        }
        s.setRcFeedMill(request.value());
        stateRepository.save(s);
    }

    // ── Good Crack sales ──────────────────────────────────────────────────

    @Audited(module = Mod.CRACK_EGG, action = "Record Sale", type = LogType.AUDIT, detail = "'Customer: ' + #request.customer()")
    @Transactional
    public GcSaleTransaction createSale(CreateGcSaleRequest request, String username) {
        validatePaymentMethods(request.paymentMethods(), request.bank());

        StockSummaryResponse summary = stockSummary();
        if (request.qty() > summary.gcClosing()) {
            throw new BadRequestException("Cannot sell " + request.qty() + " — only " + summary.gcClosing() + " Good Crack available.");
        }

        long cash = request.paymentMethods().contains(PaymentMethod.CASH) ? request.cashAmount() : 0;
        long transfer = request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.transferAmount() : 0;

        GcSaleTransaction txn = GcSaleTransaction.builder()
                .customer(request.customer()).state(request.state()).qty(request.qty()).price(request.price())
                .paymentMethods(new HashSet<>(request.paymentMethods()))
                .bank(request.paymentMethods().contains(PaymentMethod.TRANSFER) ? request.bank() : null)
                .cashAmount(cash).transferAmount(transfer).amountPaid(cash + transfer)
                .credit(request.credit()).advance(request.advance())
                .enteredBy(username)
                .build();
        return saleRepository.save(txn);
    }

    @Audited(module = Mod.CRACK_EGG, action = "Edit Sale", type = LogType.AUDIT, detail = "'Transaction #' + #id")
    @Transactional
    public GcSaleTransaction updateSale(Long id, UpdateGcSaleRequest request, String username) {
        GcSaleTransaction txn = saleRepository.findById(id).orElseThrow(() -> new NotFoundException("Transaction not found."));
        if (!isEditable(txn.getOccurredAt())) {
            throw new ForbiddenException("This transaction was recorded on a previous day and can no longer be edited.");
        }
        txn.setCustomer(request.customer());
        txn.setState(request.state());
        txn.setQty(request.qty());
        txn.setPrice(request.price());
        txn.setCredit(request.credit());
        txn.setAdvance(request.advance());
        txn.setUpdatedBy(username);
        return saleRepository.save(txn);
    }

    @Transactional(readOnly = true)
    public Page<GcSaleTransaction> saleHistory(Pageable pageable) {
        return saleRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    // ── Gift log ──────────────────────────────────────────────────────────

    /** Snapshots the CURRENT gcGiftQty/recipient/authorizer into a permanent log row — matches the frontend's saveGift(), which reads live state rather than taking new values as input. */
    @Audited(module = Mod.CRACK_EGG, action = "Save Gift", type = LogType.AUDIT)
    @Transactional
    public CrackEggGiftLogEntry saveGiftSnapshot(String username) {
        CrackEggState s = state();
        CrackEggGiftLogEntry entry = CrackEggGiftLogEntry.builder()
                .qty(s.getGcGiftQty()).recipient(s.getGcGiftRecipient()).authorizer(s.getGcGiftAuthorizer())
                .enteredBy(username)
                .build();
        return giftLogRepository.save(entry);
    }

    @Audited(module = Mod.CRACK_EGG, action = "Edit Gift Log Entry", type = LogType.AUDIT, detail = "'Entry #' + #id")
    @Transactional
    public CrackEggGiftLogEntry updateGiftEntry(Long id, UpdateGiftLogEntryRequest request, String username) {
        CrackEggGiftLogEntry entry = giftLogRepository.findById(id).orElseThrow(() -> new NotFoundException("Gift log entry not found."));
        if (!isEditable(entry.getOccurredAt())) {
            throw new ForbiddenException("This record was saved on a previous day and can no longer be edited.");
        }
        entry.setQty(request.qty());
        entry.setRecipient(request.recipient());
        entry.setAuthorizer(request.authorizer());
        entry.setUpdatedBy(username);
        return giftLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public Page<CrackEggGiftLogEntry> giftLogHistory(Pageable pageable) {
        return giftLogRepository.findAllByOrderByOccurredAtDesc(pageable);
    }

    private void validatePaymentMethods(List<PaymentMethod> methods, String bank) {
        if (methods == null || methods.isEmpty()) {
            throw new BadRequestException("Select at least one payment method.");
        }
        if (methods.contains(PaymentMethod.TRANSFER) && (bank == null || bank.isBlank())) {
            throw new BadRequestException("Please select the bank the customer transferred to.");
        }
    }

    public boolean isEditable(Instant occurredAt) {
        return occurredAt.atZone(ZONE).toLocalDate().isEqual(LocalDate.now());
    }
}

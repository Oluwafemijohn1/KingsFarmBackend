package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import com.kingsfarm.kingsfarmbackend.common.PageResponse;
import com.kingsfarm.kingsfarmbackend.common.reports.ReportTableResponse;
import com.kingsfarm.kingsfarmbackend.security.AuthenticatedPrincipal;
import com.kingsfarm.kingsfarmbackend.wholeegg.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Same role split as every other module so far: Administrator + Whole Egg Manager can read, only Whole Egg Manager can write. */
@RestController
@RequestMapping("/api/v1/whole-egg")
@PreAuthorize("hasAnyRole('ADMINISTRATOR', 'WHOLE_EGG_MANAGER')")
public class WholeEggController {

    private final WholeEggService service;

    public WholeEggController(WholeEggService service) {
        this.service = service;
    }

    // ── Stock Overview ───────────────────────────────────────────────────

    @GetMapping("/stock")
    public List<StockRowResponse> stockRows() {
        return service.stockRows();
    }

    @PatchMapping("/stock/opening")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public void setOpening(@Valid @RequestBody UpdateCategoryValueRequest request) {
        service.setOpening(request);
    }

    @PostMapping("/stock/opening/{category}/lock")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public void lockOpening(@PathVariable CatKey category) {
        service.lockOpening(category);
    }

    @PatchMapping("/stock/price")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public void setPrice(@Valid @RequestBody UpdateCategoryValueRequest request) {
        service.setPrice(request);
    }

    @PutMapping("/stock/sales-crack")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public void commitSalesCrack(@Valid @RequestBody CommitCategoryQtyRequest request) {
        service.commitSalesCrack(request);
    }

    @PutMapping("/stock/gift")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public void commitGift(@Valid @RequestBody CommitCategoryQtyRequest request) {
        service.commitGift(request);
    }

    // ── Customers ─────────────────────────────────────────────────────────

    @PostMapping("/customers")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse createCustomer(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                            @Valid @RequestBody CreateCustomerRequest request) {
        Customer customer = service.createCustomer(request, principal.username());
        // A Whole Egg Manager creates customers, never an Administrator (write-restricted above) — isAdmin is always false here.
        return service.toCustomerResponse(customer, false);
    }

    @GetMapping("/customers")
    public PageResponse<CustomerResponse> listCustomers(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                          @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.listCustomers(pageable, isAdmin(principal)));
    }

    @GetMapping("/customers/search")
    public List<CustomerResponse> searchCustomers(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestParam String q) {
        return service.searchCustomers(q, isAdmin(principal));
    }

    @GetMapping("/customers/recent")
    public List<CustomerResponse> recentCustomers(@AuthenticationPrincipal AuthenticatedPrincipal principal, @RequestParam(defaultValue = "6") int limit) {
        return service.recentCustomers(limit, isAdmin(principal));
    }

    private boolean isAdmin(AuthenticatedPrincipal principal) {
        return principal.role() == com.kingsfarm.kingsfarmbackend.user.Role.ADMINISTRATOR;
    }

    @GetMapping("/customers/{id}/history")
    public PageResponse<WeSaleTransactionResponse> customerHistory(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                                     @PathVariable Long id,
                                                                     @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.customerHistory(id, isAdmin(principal), pageable).map(service::toResponse));
    }

    // ── Sales / Payments ──────────────────────────────────────────────────

    @PostMapping("/sales")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public WeSaleTransactionResponse createSale(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                 @Valid @RequestBody CreateSaleRequest request) {
        return service.toResponse(service.createSale(request, principal.username()));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    @ResponseStatus(HttpStatus.CREATED)
    public WeSaleTransactionResponse recordPayment(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                     @Valid @RequestBody RecordPaymentRequest request) {
        return service.toResponse(service.recordPayment(request, principal.username()));
    }

    @PatchMapping("/transactions/{id}")
    @PreAuthorize("hasRole('WHOLE_EGG_MANAGER')")
    public WeSaleTransactionResponse updateTransaction(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                         @PathVariable Long id,
                                                         @Valid @RequestBody UpdateSaleTxnRequest request) {
        return service.toResponse(service.updateTransaction(id, request, principal.username()));
    }

    @GetMapping("/transactions")
    public PageResponse<WeSaleTransactionResponse> allTransactions(@AuthenticationPrincipal AuthenticatedPrincipal principal,
                                                                     @PageableDefault(size = 20) Pageable pageable) {
        return PageResponse.from(service.allTransactions(isAdmin(principal), pageable).map(service::toResponse));
    }

    @GetMapping("/customers/outstanding-summary")
    public OutstandingBalanceResponse outstandingSummary() {
        return service.outstandingTotals();
    }

    // ── Reports (BACKEND_PLAN.md §8) ─────────────────────────────────────

    @GetMapping("/reports/daily")
    public ReportTableResponse dailyReport(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return service.dailyReport(start, end);
    }

    @GetMapping("/reports/monthly")
    public ReportTableResponse monthlyReport(@RequestParam(defaultValue = "12") int months) {
        return service.monthlyReport(months);
    }
}

package com.kingsfarm.kingsfarmbackend.wholeegg;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface WeSaleTransactionRepository extends JpaRepository<WeSaleTransaction, Long> {

    /** Full history, oldest first — how the running credit/advance balance is walked (see WholeEggService.customerBalance). */
    List<WeSaleTransaction> findAllByCustomerOrderByOccurredAtAsc(Customer customer);

    /** Most recent transaction for a customer — the tail of the running-balance chain, i.e. their current balance. */
    Optional<WeSaleTransaction> findFirstByCustomerOrderByOccurredAtDesc(Customer customer);

    /** The transaction immediately before a given one for the same customer — how priorBalanceFor(txn) is computed. */
    Optional<WeSaleTransaction> findFirstByCustomerAndOccurredAtLessThanOrderByOccurredAtDesc(Customer customer, Instant occurredAt);

    Page<WeSaleTransaction> findAllByOrderByOccurredAtDesc(Pageable pageable);
    Page<WeSaleTransaction> findAllByCustomerOrderByOccurredAtDesc(Customer customer, Pageable pageable);
    Page<WeSaleTransaction> findAllByCustomerAndTxnYearOrderByOccurredAtDesc(Customer customer, int txnYear, Pageable pageable);
    Page<WeSaleTransaction> findAllByTxnYearOrderByOccurredAtDesc(int txnYear, Pageable pageable);

    /** Year-filtered sibling of findFirstByCustomerOrderByOccurredAtDesc — a manager's "Last Purchase" in the Customers directory is restricted to this year, same as their Purchase History tab. */
    Optional<WeSaleTransaction> findFirstByCustomerAndTxnYearOrderByOccurredAtDesc(Customer customer, int txnYear);

    /** "Visits" count in the Customers directory — every transaction type counts (sale, payment, opening), matching WholeEggView's customerStats(count). */
    long countByCustomer(Customer customer);
    long countByCustomerAndTxnYear(Customer customer, int txnYear);

    /** Every SALE-type transaction in a half-open [start, end) instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<WeSaleTransaction> findAllByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(WeSaleTxnType type, Instant start, Instant end);

    /** Ranks customers by how recently they last bought — feeds the New Sale picker's "recent customers" shortlist before anyone types. */
    @Query("select t.customer.id as customerId, max(t.occurredAt) as lastAt from WeSaleTransaction t group by t.customer.id order by max(t.occurredAt) desc")
    List<CustomerActivityProjection> customerActivityRanking(Pageable pageable);

    interface CustomerActivityProjection {
        Long getCustomerId();
        Instant getLastAt();
    }

    /**
     * Farm-wide "money owed to us" / "money we're holding as advance" right
     * now — sums each customer's single latest transaction's credit/advance
     * (a correlated-subquery equivalent of WholeEggView's old
     * outstandingCredit/outstandingAdvance footer math, which deduped to one
     * row per customer before summing so rolled-forward balances weren't
     * double-counted). Backs WeSalesTransactionsTable's footer now that the
     * table itself is paginated and can no longer just sum every loaded row.
     */
    @Query("select coalesce(sum(t.credit), 0) as totalCredit, coalesce(sum(t.advance), 0) as totalAdvance " +
            "from WeSaleTransaction t where t.occurredAt = " +
            "(select max(t2.occurredAt) from WeSaleTransaction t2 where t2.customer = t.customer)")
    OutstandingTotals outstandingTotals();

    interface OutstandingTotals {
        long getTotalCredit();
        long getTotalAdvance();
    }
}

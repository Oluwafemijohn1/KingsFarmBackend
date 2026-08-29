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

    /** Every SALE-type transaction in a half-open [start, end) instant range — feeds the Reports daily/monthly endpoints (BACKEND_PLAN.md §8). */
    List<WeSaleTransaction> findAllByTypeAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(WeSaleTxnType type, Instant start, Instant end);

    /** Ranks customers by how recently they last bought — feeds the New Sale picker's "recent customers" shortlist before anyone types. */
    @Query("select t.customer.id as customerId, max(t.occurredAt) as lastAt from WeSaleTransaction t group by t.customer.id order by max(t.occurredAt) desc")
    List<CustomerActivityProjection> customerActivityRanking(Pageable pageable);

    interface CustomerActivityProjection {
        Long getCustomerId();
        Instant getLastAt();
    }
}

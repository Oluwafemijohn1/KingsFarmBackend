package com.kingsfarm.kingsfarmbackend.wholeegg;

import com.kingsfarm.kingsfarmbackend.common.CatKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface WeSaleLineItemRepository extends JpaRepository<WeSaleLineItem, Long> {

    List<WeSaleLineItem> findAllByTransaction(WeSaleTransaction transaction);

    void deleteAllByTransaction(WeSaleTransaction transaction);

    /** Per-category totals across every SALE-type transaction — feeds the Stock Overview's "Total Sales" column and Production's auto-received Total Sales. */
    @Query("select li.category as category, coalesce(sum(li.qty), 0) as qty from WeSaleLineItem li " +
            "where li.transaction.type = :type group by li.category")
    List<CategoryQtyProjection> sumQtyByCategoryAndType(@Param("type") WeSaleTxnType type);

    default List<CategoryQtyProjection> sumSoldQtyByCategory() {
        return sumQtyByCategoryAndType(WeSaleTxnType.SALE);
    }

    interface CategoryQtyProjection {
        CatKey getCategory();
        long getQty();
    }
}

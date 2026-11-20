package com.example.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    /** What the ledger says an item's stock is: the sum of all its movements. */
    @Query("select coalesce(sum(m.delta), 0) from StockMovement m where m.itemId = :itemId")
    Long ledgerStock(@Param("itemId") Long itemId);
}

package com.ledger.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByUserIdAndMonth(Long userId, String month);

    List<LedgerEntry> findBySplitId(Long splitId);
}

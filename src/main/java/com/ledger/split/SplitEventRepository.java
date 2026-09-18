package com.ledger.split;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SplitEventRepository extends JpaRepository<SplitEvent, Long> {
    List<SplitEvent> findByBillIdOrderByIdAsc(Long billId);
}

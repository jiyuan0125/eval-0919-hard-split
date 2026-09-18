package com.ledger.split;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    List<Settlement> findByPayerIdOrDebtorIdOrderByIdAsc(Long payerId, Long debtorId);

    List<Settlement> findByBillId(Long billId);

    Optional<Settlement> findByBillIdAndDebtorId(Long billId, Long debtorId);
}

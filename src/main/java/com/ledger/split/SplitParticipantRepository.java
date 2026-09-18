package com.ledger.split;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SplitParticipantRepository extends JpaRepository<SplitParticipant, Long> {
    List<SplitParticipant> findByBillId(Long billId);

    List<SplitParticipant> findByUserId(Long userId);
}

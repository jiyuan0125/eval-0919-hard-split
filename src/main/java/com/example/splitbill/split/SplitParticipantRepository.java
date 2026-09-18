package com.example.splitbill.split;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SplitParticipantRepository extends JpaRepository<SplitParticipant, Long> {

    Optional<SplitParticipant> findByBillIdAndUserId(Long billId, Long userId);
}

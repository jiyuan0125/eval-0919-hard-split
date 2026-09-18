package com.example.splitbill.ledger;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LedgerRepository extends JpaRepository<LedgerEntry, Long> {

    List<LedgerEntry> findByBillId(Long billId);

    List<LedgerEntry> findByUserIdOrderByIdAsc(Long userId);

    boolean existsByRelatedEntryId(Long relatedEntryId);

    @Query("select coalesce(sum(e.amountCents), 0) from LedgerEntry e "
            + "where e.userId = :userId and e.type = :type "
            + "and e.occurredOn between :from and :to")
    long sumAmount(@Param("userId") Long userId, @Param("type") LedgerEntryType type,
                   @Param("from") LocalDate from, @Param("to") LocalDate to);
}

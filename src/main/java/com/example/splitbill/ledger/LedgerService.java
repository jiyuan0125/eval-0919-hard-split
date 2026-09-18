package com.example.splitbill.ledger;

import com.example.splitbill.common.DomainException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    public LedgerService(LedgerRepository ledgerRepository) {
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public LedgerEntry recordExpense(Long userId, long amountCents, LocalDate occurredOn, String note) {
        if (amountCents <= 0) {
            throw DomainException.badRequest("expense amount must be positive");
        }
        return ledgerRepository.save(new LedgerEntry(
                userId, LedgerEntryType.EXPENSE, amountCents, null, null, null, note, occurredOn));
    }

    @Transactional
    public LedgerEntry append(Long userId, LedgerEntryType type, long amountCents, Long billId,
                              Long counterpartyUserId, String note) {
        return ledgerRepository.save(new LedgerEntry(
                userId, type, amountCents, null, billId, counterpartyUserId, note, LocalDate.now()));
    }

    /**
     * Corrects a previous entry by appending a negating REVERSAL entry.
     * The original entry is left untouched.
     */
    @Transactional
    public LedgerEntry reverseEntry(Long entryId, String reason) {
        LedgerEntry original = ledgerRepository.findById(entryId)
                .orElseThrow(() -> DomainException.notFound("ledger entry not found: " + entryId));
        if (original.getType() == LedgerEntryType.REVERSAL) {
            throw DomainException.conflict("a reversal entry cannot be reversed");
        }
        if (ledgerRepository.existsByRelatedEntryId(entryId)) {
            throw DomainException.conflict("entry " + entryId + " has already been reversed");
        }
        LedgerEntry reversal = new LedgerEntry(
                original.getUserId(), LedgerEntryType.REVERSAL, -original.getAmountCents(),
                original.getId(), original.getBillId(), original.getCounterpartyUserId(),
                "reversal of entry " + original.getId() + ": " + reason, LocalDate.now());
        return ledgerRepository.save(reversal);
    }

    @Transactional(readOnly = true)
    public long sumExpenses(Long userId, YearMonth month) {
        return ledgerRepository.sumAmount(userId, LedgerEntryType.EXPENSE,
                month.atDay(1), month.atEndOfMonth());
    }
}

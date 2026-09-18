package com.ledger.ledger;

import com.ledger.common.Money;
import com.ledger.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.util.List;

@Service
public class LedgerService {

    private final LedgerRepository ledgerRepository;
    private final UserService userService;
    private final Clock clock;

    public LedgerService(LedgerRepository ledgerRepository, UserService userService, Clock clock) {
        this.ledgerRepository = ledgerRepository;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public LedgerEntry bookManualExpense(Long userId, long amountCents, String description) {
        userService.requireUser(userId);
        return insert(userId, currentMonth(), amountCents, LedgerEntry.Type.MANUAL_EXPENSE,
                null, null, null, description);
    }

    @Transactional
    public LedgerEntry insert(Long userId, String month, long amountCents, LedgerEntry.Type type,
                              Long splitId, Long refEntryId, Long counterpartyId, String description) {
        return insert(userId, month, amountCents, type, splitId, refEntryId, counterpartyId,
                description, null);
    }

    @Transactional
    public LedgerEntry insert(Long userId, String month, long amountCents, LedgerEntry.Type type,
                              Long splitId, Long refEntryId, Long counterpartyId, String description,
                              String reversedType) {
        return ledgerRepository.saveAndFlush(new LedgerEntry(userId, month, amountCents, type,
                splitId, refEntryId, counterpartyId, description, Instant.now(clock), reversedType));
    }



    @Transactional
    public LedgerEntry insertReversal(LedgerEntry original, long signedAmountCents, String month,
                                      String description) {
        return ledgerRepository.saveAndFlush(new LedgerEntry(original.getUserId(), month,
                signedAmountCents, LedgerEntry.Type.REVERSAL, original.getSplitId(),
                original.getId(), original.getCounterpartyId(), description, Instant.now(clock),
                original.getType().name()));
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> findBySplit(Long splitId) {
        return ledgerRepository.findBySplitId(splitId);
    }

    @Transactional(readOnly = true)
    public List<LedgerEntry> entries(Long userId, String month) {
        return ledgerRepository.findByUserIdAndMonth(userId, month);
    }

    /**
     * Total expense for progress-bar purposes: plain manual expenses plus each
     * participant's own share of finalized splits. Receivables, settlements and
     * reversals net to zero here and are not counted as new spending.
     */
    @Transactional(readOnly = true)
    public long spentInMonth(Long userId, YearMonth month) {
        String key = month.toString();
        long total = 0;
        for (LedgerEntry entry : ledgerRepository.findByUserIdAndMonth(userId, key)) {
            switch (entry.getType()) {
                case MANUAL_EXPENSE, SPLIT_EXPENSE -> total += entry.getAmountCents();
                // only a reversal of an expense changes spending; reversals of
                // receivables/settlements merely move money and net to zero here
                case REVERSAL -> {
                    if (LedgerEntry.Type.SPLIT_EXPENSE.name().equals(entry.getReversedType())
                            || LedgerEntry.Type.MANUAL_EXPENSE.name().equals(entry.getReversedType())) {
                        total += entry.getAmountCents();
                    }
                }
                default -> {
                }
            }
        }
        return total;
    }

    public String currentMonth() {
        return YearMonth.now(clock).toString();
    }

    public String yuan(long cents) {
        return Money.centsToYuan(cents);
    }
}

package com.example.splitbill.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.splitbill.common.DomainException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class LedgerServiceTest {

    @Autowired
    LedgerService ledgerService;
    @Autowired
    LedgerRepository ledgerRepository;

    @Test
    void reversalNegatesOriginalWithoutModifyingIt() {
        LedgerEntry original = ledgerService.recordExpense(1L, 4200, LocalDate.of(2026, 9, 10), "coffee");

        LedgerEntry reversal = ledgerService.reverseEntry(original.getId(), "duplicate");

        assertThat(reversal.getType()).isEqualTo(LedgerEntryType.REVERSAL);
        assertThat(reversal.getAmountCents()).isEqualTo(-4200);
        assertThat(reversal.getRelatedEntryId()).isEqualTo(original.getId());
        LedgerEntry reloaded = ledgerRepository.findById(original.getId()).orElseThrow();
        assertThat(reloaded.getAmountCents()).isEqualTo(4200);
        assertThat(reloaded.getType()).isEqualTo(LedgerEntryType.EXPENSE);
    }

    @Test
    void entryCanOnlyBeReversedOnce() {
        LedgerEntry original = ledgerService.recordExpense(1L, 1000, LocalDate.of(2026, 9, 1), "x");
        ledgerService.reverseEntry(original.getId(), "first");

        assertThatThrownBy(() -> ledgerService.reverseEntry(original.getId(), "second"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void reversalEntryItselfCannotBeReversed() {
        LedgerEntry original = ledgerService.recordExpense(1L, 1000, LocalDate.of(2026, 9, 1), "x");
        LedgerEntry reversal = ledgerService.reverseEntry(original.getId(), "fix");

        assertThatThrownBy(() -> ledgerService.reverseEntry(reversal.getId(), "undo"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void sumExpensesOnlyCountsGivenMonth() {
        ledgerService.recordExpense(1L, 1000, LocalDate.of(2026, 9, 1), "a");
        ledgerService.recordExpense(1L, 2500, LocalDate.of(2026, 9, 30), "b");
        ledgerService.recordExpense(1L, 9999, LocalDate.of(2026, 8, 31), "other month");
        ledgerService.recordExpense(2L, 7777, LocalDate.of(2026, 9, 15), "other user");

        assertThat(ledgerService.sumExpenses(1L, YearMonth.of(2026, 9))).isEqualTo(3500);
        assertThat(ledgerService.sumExpenses(1L, YearMonth.of(2026, 8))).isEqualTo(9999);
    }

    @Test
    void rejectsNonPositiveExpense() {
        assertThatThrownBy(() -> ledgerService.recordExpense(1L, 0, LocalDate.now(), "bad"))
                .isInstanceOf(DomainException.class);
    }
}

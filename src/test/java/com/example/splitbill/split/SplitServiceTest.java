package com.example.splitbill.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.splitbill.common.DomainException;
import com.example.splitbill.ledger.LedgerEntry;
import com.example.splitbill.ledger.LedgerEntryType;
import com.example.splitbill.ledger.LedgerRepository;
import com.example.splitbill.user.AppUser;
import com.example.splitbill.user.UserRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class SplitServiceTest {

    @Autowired
    SplitService splitService;
    @Autowired
    UserRepository userRepository;
    @Autowired
    LedgerRepository ledgerRepository;

    Long payer;
    Long alice;
    Long bob;
    Long carol;

    @BeforeEach
    void setUp() {
        payer = userRepository.save(new AppUser("payer")).getId();
        alice = userRepository.save(new AppUser("alice")).getId();
        bob = userRepository.save(new AppUser("bob")).getId();
        carol = userRepository.save(new AppUser("carol")).getId();
    }

    @Test
    void createBillSplitsDeterministicallyAndSumsToTotal() {
        // 100.00 among payer + 2 friends: payer 3333, pool 6667 -> 3334 + 3333.
        SplitBill bill = splitService.createBill(payer, "dinner", 10000, List.of(alice, bob));

        assertThat(bill.getPayerShareCents()).isEqualTo(3333);
        assertThat(bill.getParticipants()).hasSize(2);
        long friendSum = bill.getParticipants().stream().mapToLong(SplitParticipant::getShareCents).sum();
        assertThat(friendSum + bill.getPayerShareCents()).isEqualTo(10000);
        assertThat(shareOf(bill, alice)).isEqualTo(3334);
        assertThat(shareOf(bill, bob)).isEqualTo(3333);
    }

    @Test
    void confirmFreezesShareAndBooksLedgerEntry() {
        SplitBill bill = splitService.createBill(payer, "taxi", 9000, List.of(alice, bob));

        splitService.confirm(bill.getId(), alice);

        SplitParticipant confirmed = participantOf(bill.getId(), alice);
        assertThat(confirmed.getStatus()).isEqualTo(ParticipantStatus.CONFIRMED);
        List<LedgerEntry> entries = ledgerRepository.findByUserIdOrderByIdAsc(alice);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getType()).isEqualTo(LedgerEntryType.SPLIT_SHARE);
        assertThat(entries.get(0).getAmountCents()).isEqualTo(3000);
    }

    @Test
    void withdrawRecomputesPendingSharesButKeepsConfirmedFrozen() {
        // total 10000, payer share 2500, pool 7500 -> 2500 each for 3 friends.
        SplitBill bill = splitService.createBill(payer, "trip", 10000, List.of(alice, bob, carol));
        splitService.confirm(bill.getId(), alice); // frozen at 2500

        splitService.withdraw(bill.getId(), bob);

        assertThat(participantOf(bill.getId(), bob).getStatus()).isEqualTo(ParticipantStatus.WITHDRAWN);
        // confirmed share untouched
        assertThat(participantOf(bill.getId(), alice).getShareCents()).isEqualTo(2500);
        // remaining pool 10000 - 2500 (payer) - 2500 (alice) = 5000 goes to carol alone
        assertThat(participantOf(bill.getId(), carol).getShareCents()).isEqualTo(5000);
    }

    @Test
    void confirmedParticipantCannotWithdraw() {
        SplitBill bill = splitService.createBill(payer, "lunch", 6000, List.of(alice, bob));
        splitService.confirm(bill.getId(), alice);

        assertThatThrownBy(() -> splitService.withdraw(bill.getId(), alice))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("reversal");
    }

    @Test
    void doubleConfirmAndDoubleWithdrawAreRejected() {
        SplitBill bill = splitService.createBill(payer, "lunch", 6000, List.of(alice, bob));
        splitService.confirm(bill.getId(), alice);
        assertThatThrownBy(() -> splitService.confirm(bill.getId(), alice))
                .isInstanceOf(DomainException.class);

        SplitBill bill2 = splitService.createBill(payer, "lunch2", 6000, List.of(alice, bob));
        splitService.withdraw(bill2.getId(), alice);
        assertThatThrownBy(() -> splitService.withdraw(bill2.getId(), alice))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.confirm(bill2.getId(), alice))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void billSettlesWhenAllRemainingParticipantsConfirmed() {
        SplitBill bill = splitService.createBill(payer, "dinner", 9000, List.of(alice, bob));
        splitService.confirm(bill.getId(), alice);
        SplitBill settled = splitService.confirm(bill.getId(), bob);

        assertThat(settled.getStatus()).isEqualTo(BillStatus.SETTLED);
        List<LedgerEntry> settlements = ledgerRepository.findByBillId(bill.getId()).stream()
                .filter(e -> e.getType() == LedgerEntryType.SETTLEMENT).toList();
        assertThat(settlements).hasSize(2);
        assertThat(settlements).allSatisfy(e -> {
            assertThat(e.getCounterpartyUserId()).isEqualTo(payer);
            assertThat(e.getAmountCents()).isEqualTo(3000);
        });
    }

    @Test
    void billIsCancelledWhenEveryoneWithdraws() {
        SplitBill bill = splitService.createBill(payer, "snacks", 5000, List.of(alice));
        SplitBill result = splitService.withdraw(bill.getId(), alice);
        assertThat(result.getStatus()).isEqualTo(BillStatus.CANCELLED);
    }

    @Test
    void reverseBillAppendsReversalsAndNeverModifiesOriginals() {
        SplitBill bill = splitService.createBill(payer, "dinner", 9000, List.of(alice, bob));
        splitService.confirm(bill.getId(), alice);
        splitService.confirm(bill.getId(), bob);
        List<LedgerEntry> before = ledgerRepository.findByBillId(bill.getId());
        assertThat(before).hasSize(4); // 2 split shares + 2 settlements

        SplitBill reversed = splitService.reverseBill(bill.getId(), "wrong amount");

        assertThat(reversed.getStatus()).isEqualTo(BillStatus.CANCELLED);
        List<LedgerEntry> after = ledgerRepository.findByBillId(bill.getId());
        assertThat(after).hasSize(8); // 4 originals untouched + 4 reversals
        List<LedgerEntry> reversals = after.stream()
                .filter(e -> e.getType() == LedgerEntryType.REVERSAL).toList();
        assertThat(reversals).hasSize(4);
        assertThat(reversals).allSatisfy(e -> assertThat(e.getAmountCents()).isNegative());
        // net effect per user is zero
        for (Long user : List.of(alice, bob)) {
            long net = after.stream().filter(e -> e.getUserId().equals(user))
                    .mapToLong(LedgerEntry::getAmountCents).sum();
            assertThat(net).isZero();
        }
        // reversing again is rejected
        assertThatThrownBy(() -> splitService.reverseBill(bill.getId(), "again"))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void closedBillRejectsFurtherActions() {
        SplitBill bill = splitService.createBill(payer, "dinner", 9000, List.of(alice, bob));
        splitService.confirm(bill.getId(), alice);
        splitService.confirm(bill.getId(), bob);

        assertThatThrownBy(() -> splitService.withdraw(bill.getId(), bob))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.confirm(bill.getId(), alice))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectsInvalidBills() {
        assertThatThrownBy(() -> splitService.createBill(payer, "x", 0, List.of(alice)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.createBill(payer, "x", 100, List.of()))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.createBill(payer, "x", 100, List.of(alice, alice)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.createBill(payer, "x", 100, List.of(payer)))
                .isInstanceOf(DomainException.class);
        assertThatThrownBy(() -> splitService.createBill(payer, "x", 100, List.of(999999L)))
                .isInstanceOf(DomainException.class);
    }

    private long shareOf(SplitBill bill, Long userId) {
        return bill.getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId)).findFirst().orElseThrow().getShareCents();
    }

    private SplitParticipant participantOf(Long billId, Long userId) {
        return splitService.getBill(billId).getParticipants().stream()
                .filter(p -> p.getUserId().equals(userId)).findFirst().orElseThrow();
    }
}

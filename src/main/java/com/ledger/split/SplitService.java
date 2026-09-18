package com.ledger.split;

import com.ledger.common.ApiException;
import com.ledger.common.ErrorCode;
import com.ledger.common.Money;
import com.ledger.ledger.LedgerEntry;
import com.ledger.ledger.LedgerService;
import com.ledger.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SplitService {

    private final SplitBillRepository billRepository;
    private final SplitParticipantRepository participantRepository;
    private final SplitEventRepository eventRepository;
    private final SettlementRepository settlementRepository;
    private final LedgerService ledgerService;
    private final UserService userService;
    private final Clock clock;

    public SplitService(SplitBillRepository billRepository,
                        SplitParticipantRepository participantRepository,
                        SplitEventRepository eventRepository,
                        SettlementRepository settlementRepository,
                        LedgerService ledgerService,
                        UserService userService,
                        Clock clock) {
        this.billRepository = billRepository;
        this.participantRepository = participantRepository;
        this.eventRepository = eventRepository;
        this.settlementRepository = settlementRepository;
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.clock = clock;
    }

    // ---------- create ----------

    /**
     * Create a bill paid by {@code payerId} and shared equally among payer +
     * participant ids. Cent allocation is deterministic (see Money.allocate).
     */
    @Transactional
    public BillDetail createBill(Long payerId, long totalCents, List<Long> participantIds,
                                 String description, Long predecessorId) {
        userService.requireUser(payerId);
        if (totalCents <= 0) {
            throw new ApiException(ErrorCode.VALIDATION, "split total must be positive");
        }
        List<Long> userIds = normaliseParticipants(payerId, participantIds);
        userIds.forEach(userService::requireUser);
        if (userIds.size() < 2) {
            throw new ApiException(ErrorCode.VALIDATION, "a split needs at least 2 participants");
        }
        if (predecessorId != null && !billRepository.existsById(predecessorId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "predecessor bill not found: " + predecessorId);
        }

        String month = ledgerService.currentMonth();
        SplitBill bill = billRepository.saveAndFlush(new SplitBill(
                payerId, totalCents, month, description, predecessorId, Instant.now(clock)));

        List<Money.Weighted<Long>> weights = userIds.stream()
                .map(uid -> new Money.Weighted<>(uid, 1L))
                .toList();
        List<Money.Share<Long>> shares = Money.allocate(totalCents, weights);
        for (Money.Share<Long> share : shares) {
            participantRepository.saveAndFlush(new SplitParticipant(bill.getId(), share.key(), share.amountCents()));
        }
        eventRepository.saveAndFlush(new SplitEvent(bill.getId(), SplitEvent.Type.CREATED,
                payerId, predecessorId == null ? null : "successor of bill " + predecessorId,
                Instant.now(clock)));
        return loadBill(bill.getId());
    }

    private List<Long> normaliseParticipants(Long payerId, List<Long> participantIds) {
        if (participantIds == null) {
            throw new ApiException(ErrorCode.VALIDATION, "participantIds must not be null");
        }
        Set<Long> given = new HashSet<>(participantIds);
        if (given.size() != participantIds.size()) {
            throw new ApiException(ErrorCode.VALIDATION, "duplicate participant ids");
        }
        given.add(payerId);
        return given.stream().sorted().toList();
    }

    // ---------- read ----------

    @Transactional(readOnly = true)
    public BillDetail loadBill(Long billId) {
        SplitBill bill = billRepository.findById(billId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "bill not found: " + billId));
        List<SplitParticipant> participants = participantRepository.findByBillId(billId).stream()
                .sorted((a, b) -> a.getUserId().compareTo(b.getUserId()))
                .toList();
        List<SplitEvent> events = eventRepository.findByBillIdOrderByIdAsc(billId);
        List<Settlement> settlements = settlementRepository.findByBillId(billId);
        List<Long> confirmed = events.stream()
                .filter(e -> e.getType() == SplitEvent.Type.CONFIRMED)
                .map(SplitEvent::getActorId)
                .distinct()
                .sorted()
                .toList();
        return new BillDetail(bill, deriveStatus(events), participants, events, settlements, confirmed);
    }

    @Transactional(readOnly = true)
    public BillDetail requireParticipantBill(Long userId, Long billId) {
        BillDetail detail = loadBill(billId);
        boolean participant = detail.participants().stream()
                .anyMatch(p -> p.getUserId().equals(userId));
        if (!participant) {
            throw new ApiException(ErrorCode.FORBIDDEN, "you are not a participant of bill " + billId);
        }
        return detail;
    }

    static SplitStatus deriveStatus(List<SplitEvent> events) {
        SplitStatus status = SplitStatus.INITIATED;
        for (SplitEvent event : events) {
            switch (event.getType()) {
                case VOIDED -> status = SplitStatus.VOIDED;
                case FINALIZED -> status = SplitStatus.FINALIZED;
                case REVERSED -> status = SplitStatus.REVERSED;
                default -> {
                }
            }
        }
        return status;
    }

    // ---------- confirm / finalize ----------

    /**
     * A participant confirms their share. The payer is considered the initiator
     * and does not confirm. When every non-payer participant has confirmed, the
     * bill finalizes atomically and shares land in everyone's ledger.
     */
    @Transactional
    public BillDetail confirm(Long userId, Long billId) {
        BillDetail detail = requireParticipantBill(userId, billId);
        if (detail.status() != SplitStatus.INITIATED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "bill is " + detail.status() + ", only INITIATED bills accept confirmations");
        }
        if (userId.equals(detail.bill().getPayerId())) {
            throw new ApiException(ErrorCode.VALIDATION, "the payer does not need to confirm");
        }
        boolean already = detail.events().stream()
                .anyMatch(e -> e.getType() == SplitEvent.Type.CONFIRMED && e.getActorId().equals(userId));
        if (!already) {
            eventRepository.saveAndFlush(new SplitEvent(billId, SplitEvent.Type.CONFIRMED, userId,
                    null, Instant.now(clock)));
        }

        detail = loadBill(billId);
        if (allNonPayersConfirmed(detail)) {
            finalize(detail);
            detail = loadBill(billId);
        }
        return detail;
    }

    private boolean allNonPayersConfirmed(BillDetail detail) {
        Set<Long> confirmed = new HashSet<>(detail.confirmedUserIds());
        return detail.participants().stream()
                .filter(p -> !p.getUserId().equals(detail.bill().getPayerId()))
                .allMatch(p -> confirmed.contains(p.getUserId()));
    }

    private void finalize(BillDetail detail) {
        Long billId = detail.bill().getId();
        String month = detail.bill().getMonth();
        Long payerId = detail.bill().getPayerId();
        for (SplitParticipant p : detail.participants()) {
            LedgerEntry.Type type = p.getUserId().equals(payerId)
                    ? LedgerEntry.Type.SPLIT_RECEIVABLE : LedgerEntry.Type.SPLIT_EXPENSE;
            Long counterparty = p.getUserId().equals(payerId) ? null : payerId;
            ledgerService.insert(p.getUserId(), month, p.getShareCents(), type,
                    billId, null, counterparty, detail.bill().getDescription());
        }
        eventRepository.saveAndFlush(new SplitEvent(billId, SplitEvent.Type.FINALIZED, payerId,
                "all participants confirmed", Instant.now(clock)));
    }

    // ---------- mid-bill withdrawal / cancellation ----------

    /**
     * A non-payer leaves an INITIATED bill.
     *
     * <p>Policy: the existing bill is never edited - it is VOIDED as a whole
     * (previous confirmations stay visible on its event stream but no longer
     * count), shares are recomputed deterministically among the remaining
     * participants, and a fresh successor bill is created that everyone must
     * confirm again. Nothing has touched anyone's ledger before finalization, so
     * no correcting ledger lines are needed.
     */
    @Transactional
    public BillDetail withdraw(Long userId, Long billId, String reason) {
        BillDetail detail = requireParticipantBill(userId, billId);
        if (detail.status() != SplitStatus.INITIATED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "cannot withdraw from a " + detail.status() + " bill; use reversal");
        }
        if (userId.equals(detail.bill().getPayerId())) {
            throw new ApiException(ErrorCode.VALIDATION,
                    "the payer cancels the whole bill instead of withdrawing");
        }
        List<Long> remaining = detail.participants().stream()
                .map(SplitParticipant::getUserId)
                .filter(uid -> !uid.equals(userId))
                .toList();
        voidBill(billId, userId, "user " + userId + " withdrew: "
                + (reason == null ? "" : reason));
        return createBill(detail.bill().getPayerId(), detail.bill().getTotalCents(),
                remaining, detail.bill().getDescription(), billId);
    }

    /** Payer calls off an INITIATED bill outright; it never finalizes. */
    @Transactional
    public BillDetail cancel(Long payerId, Long billId, String reason) {
        BillDetail detail = loadBill(billId);
        if (!detail.bill().getPayerId().equals(payerId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "only the payer can cancel the bill");
        }
        if (detail.status() != SplitStatus.INITIATED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "cannot cancel a " + detail.status() + " bill; use reversal");
        }
        voidBill(billId, payerId, "cancelled by payer: " + (reason == null ? "" : reason));
        return loadBill(billId);
    }

    private void voidBill(Long billId, Long actorId, String detail) {
        eventRepository.saveAndFlush(new SplitEvent(billId, SplitEvent.Type.VOIDED,
                actorId, detail, Instant.now(clock)));
    }

    // ---------- post-finalize reversal ----------

    /**
     * Reverse a FINALIZED bill because it was wrong. The original rows are never
     * modified: every ledger line the finalization (and any settlements) created
     * is paired with a negative REVERSAL line in the current month, and the bill
     * gets a REVERSED event. A corrected bill can then be created normally.
     */
    @Transactional
    public BillDetail reverse(Long actorId, Long billId, String reason) {
        BillDetail detail = loadBill(billId);
        if (!detail.bill().getPayerId().equals(actorId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "only the payer can reverse the bill");
        }
        if (detail.status() != SplitStatus.FINALIZED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "only FINALIZED bills can be reversed; current state: " + detail.status());
        }
        String reversalMonth = ledgerService.currentMonth();
        reverseLedgerLines(billId, reversalMonth);
        for (Settlement settlement : detail.settlements()) {
            reverseSettlementLines(settlement, reversalMonth);
        }
        eventRepository.saveAndFlush(new SplitEvent(billId, SplitEvent.Type.REVERSED,
                actorId, "reversal: " + (reason == null ? "" : reason), Instant.now(clock)));
        return loadBill(billId);
    }

    private void reverseLedgerLines(Long billId, String month) {
        // Mirror the exact original finalization lines (negated), never recomputed:
        // the historical record stays authoritative.
        List<LedgerEntry> originals = ledgerService.findBySplit(billId).stream()
                .filter(e -> e.getType() == LedgerEntry.Type.SPLIT_EXPENSE
                        || e.getType() == LedgerEntry.Type.SPLIT_RECEIVABLE)
                .toList();
        for (LedgerEntry original : originals) {
            ledgerService.insertReversal(original, -original.getAmountCents(), month,
                    "reverse bill " + billId + " line " + original.getId());
        }
    }

    private void reverseSettlementLines(Settlement settlement, String month) {
        ledgerService.insert(settlement.getDebtorId(), month, -settlement.getAmountCents(),
                LedgerEntry.Type.REVERSAL, settlement.getBillId(), settlement.getBillId(),
                settlement.getPayerId(), "reverse settlement of bill " + settlement.getBillId(),
                LedgerEntry.Type.SETTLEMENT_PAID.name());
        ledgerService.insert(settlement.getPayerId(), month, -settlement.getAmountCents(),
                LedgerEntry.Type.REVERSAL, settlement.getBillId(), settlement.getBillId(),
                settlement.getDebtorId(), "reverse settlement of bill " + settlement.getBillId(),
                LedgerEntry.Type.SETTLEMENT_RECEIVED.name());
    }

    // ---------- settlement ----------

    /**
     * A non-payer participant settles their whole share of one bill with the
     * payer. Two symmetric ledger lines are written (payer receives, debtor
     * pays); the settlement itself is an immutable row and is idempotent.
     */
    @Transactional
    public Settlement settle(Long debtorId, Long billId) {
        BillDetail detail = loadBill(billId);
        if (detail.status() != SplitStatus.FINALIZED) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "only FINALIZED bills can be settled; current state: " + detail.status());
        }
        if (debtorId.equals(detail.bill().getPayerId())) {
            throw new ApiException(ErrorCode.VALIDATION, "the payer does not settle their own bill");
        }
        requireParticipantBill(debtorId, billId);

        return settlementRepository.findByBillIdAndDebtorId(billId, debtorId)
                .orElseGet(() -> {
                    SplitParticipant self = detail.participants().stream()
                            .filter(p -> p.getUserId().equals(debtorId))
                            .findFirst()
                            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "participant missing"));
                    String month = ledgerService.currentMonth();
                    ledgerService.insert(debtorId, month, self.getShareCents(),
                            LedgerEntry.Type.SETTLEMENT_PAID, billId, null,
                            detail.bill().getPayerId(), "settle bill " + billId);
                    ledgerService.insert(detail.bill().getPayerId(), month, self.getShareCents(),
                            LedgerEntry.Type.SETTLEMENT_RECEIVED, billId, null,
                            debtorId, "received settlement for bill " + billId);
                    return settlementRepository.saveAndFlush(new Settlement(billId,
                            detail.bill().getPayerId(), debtorId, self.getShareCents(), Instant.now(clock)));
                });
    }

    /**
     * Outstanding settlement balance of {@code userId}: share owed on finalized,
     * non-reversed bills where the user is a non-payer, minus what is already
     * settled. Positive means the user owes that amount in total.
     */
    @Transactional(readOnly = true)
    public Outstanding outstanding(Long userId) {
        long owedCents = 0;
        long settledCents = 0;
        List<BillLine> lines = new java.util.ArrayList<>();

        List<SplitParticipant> mine = participantIdsOfUser(userId);
        for (SplitParticipant participant : mine) {
            SplitBill bill = billRepository.findById(participant.getBillId()).orElseThrow();
            if (bill.getPayerId().equals(userId)) {
                continue;
            }
            BillDetail detail = loadBill(bill.getId());
            if (detail.status() != SplitStatus.FINALIZED) {
                continue;
            }
            long settled = detail.settlements().stream()
                    .filter(s -> s.getDebtorId().equals(userId))
                    .mapToLong(Settlement::getAmountCents)
                    .sum();
            if (settled >= participant.getShareCents()) {
                continue;
            }
            owedCents += participant.getShareCents();
            settledCents += settled;
            lines.add(new BillLine(bill.getId(), bill.getPayerId(),
                    participant.getShareCents() - settled, participant.getShareCents()));
        }
        return new Outstanding(owedCents - settledCents, lines);
    }

    private List<SplitParticipant> participantIdsOfUser(Long userId) {
        return participantRepository.findByUserId(userId);
    }

    public record BillLine(Long billId, Long payerId, long outstandingCents, long shareCents) {
    }

    public record Outstanding(long totalCents, List<BillLine> bills) {
    }
}

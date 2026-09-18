package com.example.splitbill.split;

import com.example.splitbill.common.DomainException;
import com.example.splitbill.ledger.LedgerEntry;
import com.example.splitbill.ledger.LedgerEntryType;
import com.example.splitbill.ledger.LedgerRepository;
import com.example.splitbill.ledger.LedgerService;
import com.example.splitbill.user.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SplitService {

    private final SplitBillRepository billRepository;
    private final SplitParticipantRepository participantRepository;
    private final LedgerRepository ledgerRepository;
    private final LedgerService ledgerService;
    private final UserRepository userRepository;

    public SplitService(SplitBillRepository billRepository,
                        SplitParticipantRepository participantRepository,
                        LedgerRepository ledgerRepository,
                        LedgerService ledgerService,
                        UserRepository userRepository) {
        this.billRepository = billRepository;
        this.participantRepository = participantRepository;
        this.ledgerRepository = ledgerRepository;
        this.ledgerService = ledgerService;
        this.userRepository = userRepository;
    }

    /**
     * Creates a bill. The payer's own share is fixed as floor(total / (friends + 1));
     * the remaining pool (total - payerShare) is split among friends by the
     * deterministic {@link SplitCalculator} rule.
     */
    @Transactional
    public SplitBill createBill(Long payerId, String title, long totalCents, List<Long> friendIds) {
        if (totalCents <= 0) {
            throw DomainException.badRequest("total amount must be positive");
        }
        if (friendIds == null || friendIds.isEmpty()) {
            throw DomainException.badRequest("at least one friend is required");
        }
        Set<Long> uniqueFriends = new LinkedHashSet<>(friendIds);
        if (uniqueFriends.size() != friendIds.size()) {
            throw DomainException.badRequest("duplicate friend ids are not allowed");
        }
        if (uniqueFriends.contains(payerId)) {
            throw DomainException.badRequest("payer cannot be a participant of their own bill");
        }
        requireUser(payerId);
        uniqueFriends.forEach(this::requireUser);

        long payerShare = totalCents / (uniqueFriends.size() + 1);
        long pool = totalCents - payerShare;
        Map<Long, Long> shares = SplitCalculator.splitEqual(pool, List.copyOf(uniqueFriends));

        SplitBill bill = new SplitBill(payerId, title, totalCents, payerShare);
        shares.forEach((userId, share) -> bill.addParticipant(new SplitParticipant(userId, share)));
        return billRepository.save(bill);
    }

    /**
     * Confirms the caller's share: it is frozen and booked to their own ledger.
     */
    @Transactional
    public SplitBill confirm(Long billId, Long userId) {
        SplitBill bill = requireOpenBill(billId);
        SplitParticipant participant = requireParticipant(billId, userId);
        if (participant.getStatus() == ParticipantStatus.CONFIRMED) {
            throw DomainException.conflict("participant " + userId + " has already confirmed");
        }
        if (participant.getStatus() == ParticipantStatus.WITHDRAWN) {
            throw DomainException.conflict("participant " + userId + " has withdrawn");
        }
        participant.setStatus(ParticipantStatus.CONFIRMED);
        participant.setConfirmedAt(Instant.now());
        ledgerService.append(userId, LedgerEntryType.SPLIT_SHARE, participant.getShareCents(),
                billId, bill.getPayerId(), "split share for bill " + billId);
        finalizeIfComplete(bill);
        return bill;
    }

    /**
     * Withdraws a PENDING participant. Confirmed shares (and the payer share) stay
     * frozen; the remaining pool is re-split deterministically among the friends
     * who are still pending.
     */
    @Transactional
    public SplitBill withdraw(Long billId, Long userId) {
        SplitBill bill = requireOpenBill(billId);
        SplitParticipant participant = requireParticipant(billId, userId);
        if (participant.getStatus() == ParticipantStatus.CONFIRMED) {
            throw DomainException.conflict(
                    "confirmed shares cannot be withdrawn; correct them with a reversal instead");
        }
        if (participant.getStatus() == ParticipantStatus.WITHDRAWN) {
            throw DomainException.conflict("participant " + userId + " has already withdrawn");
        }
        participant.setStatus(ParticipantStatus.WITHDRAWN);
        redistribute(bill);
        finalizeIfComplete(bill);
        return bill;
    }

    /**
     * Reverses a whole bill: appends a REVERSAL ledger entry negating every
     * non-reversal entry of the bill, then marks the bill CANCELLED. Nothing is
     * updated or deleted; the correction lives entirely in new ledger rows.
     */
    @Transactional
    public SplitBill reverseBill(Long billId, String reason) {
        SplitBill bill = billRepository.findById(billId)
                .orElseThrow(() -> DomainException.notFound("bill not found: " + billId));
        if (bill.getStatus() == BillStatus.CANCELLED) {
            throw DomainException.conflict("bill " + billId + " is already cancelled");
        }
        List<LedgerEntry> entries = ledgerRepository.findByBillId(billId).stream()
                .filter(e -> e.getType() != LedgerEntryType.REVERSAL)
                .toList();
        for (LedgerEntry entry : entries) {
            ledgerService.reverseEntry(entry.getId(), reason);
        }
        bill.setStatus(BillStatus.CANCELLED);
        return bill;
    }

    @Transactional(readOnly = true)
    public SplitBill getBill(Long billId) {
        return billRepository.findById(billId)
                .orElseThrow(() -> DomainException.notFound("bill not found: " + billId));
    }

    private void redistribute(SplitBill bill) {
        List<SplitParticipant> pending = bill.getParticipants().stream()
                .filter(p -> p.getStatus() == ParticipantStatus.PENDING)
                .sorted(Comparator.comparing(SplitParticipant::getUserId))
                .toList();
        if (pending.isEmpty()) {
            return;
        }
        long confirmedSum = bill.getParticipants().stream()
                .filter(p -> p.getStatus() == ParticipantStatus.CONFIRMED)
                .mapToLong(SplitParticipant::getShareCents)
                .sum();
        long pool = bill.getTotalCents() - bill.getPayerShareCents() - confirmedSum;
        Map<Long, Long> shares = SplitCalculator.splitEqual(
                pool, pending.stream().map(SplitParticipant::getUserId).toList());
        pending.forEach(p -> p.setShareCents(shares.get(p.getUserId())));
    }

    private void finalizeIfComplete(SplitBill bill) {
        boolean anyPending = bill.getParticipants().stream()
                .anyMatch(p -> p.getStatus() == ParticipantStatus.PENDING);
        if (anyPending || bill.getStatus() != BillStatus.OPEN) {
            return;
        }
        List<SplitParticipant> confirmed = bill.getParticipants().stream()
                .filter(p -> p.getStatus() == ParticipantStatus.CONFIRMED)
                .toList();
        if (confirmed.isEmpty()) {
            bill.setStatus(BillStatus.CANCELLED);
            return;
        }
        for (SplitParticipant participant : confirmed) {
            ledgerService.append(participant.getUserId(), LedgerEntryType.SETTLEMENT,
                    participant.getShareCents(), bill.getId(), bill.getPayerId(),
                    "settlement: user " + participant.getUserId() + " pays " + bill.getPayerId());
        }
        bill.setStatus(BillStatus.SETTLED);
    }

    private SplitBill requireOpenBill(Long billId) {
        SplitBill bill = getBill(billId);
        if (bill.getStatus() != BillStatus.OPEN) {
            throw DomainException.conflict("bill " + billId + " is " + bill.getStatus());
        }
        return bill;
    }

    private SplitParticipant requireParticipant(Long billId, Long userId) {
        return participantRepository.findByBillIdAndUserId(billId, userId)
                .orElseThrow(() -> DomainException.notFound(
                        "user " + userId + " is not a participant of bill " + billId));
    }

    private void requireUser(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw DomainException.notFound("user not found: " + userId);
        }
    }
}

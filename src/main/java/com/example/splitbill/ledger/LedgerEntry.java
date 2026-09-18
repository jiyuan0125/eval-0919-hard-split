package com.example.splitbill.ledger;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Append-only ledger record. Once persisted it is never updated or deleted;
 * corrections are expressed by adding a {@link LedgerEntryType#REVERSAL} entry
 * that references the original entry id.
 */
@Entity
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    @Enumerated(EnumType.STRING)
    private LedgerEntryType type;

    private long amountCents;

    private Long relatedEntryId;

    private Long billId;

    private Long counterpartyUserId;

    private String note;

    private LocalDate occurredOn;

    private Instant createdAt = Instant.now();

    protected LedgerEntry() {
    }

    public LedgerEntry(Long userId, LedgerEntryType type, long amountCents, Long relatedEntryId,
                       Long billId, Long counterpartyUserId, String note, LocalDate occurredOn) {
        this.userId = userId;
        this.type = type;
        this.amountCents = amountCents;
        this.relatedEntryId = relatedEntryId;
        this.billId = billId;
        this.counterpartyUserId = counterpartyUserId;
        this.note = note;
        this.occurredOn = occurredOn;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LedgerEntryType getType() {
        return type;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Long getRelatedEntryId() {
        return relatedEntryId;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getCounterpartyUserId() {
        return counterpartyUserId;
    }

    public String getNote() {
        return note;
    }

    public LocalDate getOccurredOn() {
        return occurredOn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

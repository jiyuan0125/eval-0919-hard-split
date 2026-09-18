package com.ledger.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Immutable ledger line. Rows are only ever inserted; corrections are expressed
 * as additional reversing rows ({@link Type#REVERSAL}), never by UPDATE.
 */
@Entity
@Table(name = "ledger_entry", indexes = {
        @Index(name = "idx_ledger_user_month", columnList = "user_id,month_key")
})
public class LedgerEntry {

    public enum Type {
        MANUAL_EXPENSE,
        SPLIT_EXPENSE,
        SPLIT_RECEIVABLE,
        SETTLEMENT_PAID,
        SETTLEMENT_RECEIVED,
        REVERSAL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "month_key", nullable = false, updatable = false)
    private String month;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private Type type;

    @Column(name = "split_id", updatable = false)
    private Long splitId;

    @Column(name = "ref_entry_id", updatable = false)
    private Long refEntryId;

    /** For REVERSAL rows: the type of the original line being negated. */
    @Column(name = "reversed_type", updatable = false, length = 24)
    private String reversedType;

    @Column(name = "counterparty_id", updatable = false)
    private Long counterpartyId;

    @Column(name = "description", updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long userId, String month, long amountCents, Type type,
                       Long splitId, Long refEntryId, Long counterpartyId,
                       String description, Instant createdAt) {
        this(userId, month, amountCents, type, splitId, refEntryId, counterpartyId,
                description, createdAt, null);
    }

    public LedgerEntry(Long userId, String month, long amountCents, Type type,
                       Long splitId, Long refEntryId, Long counterpartyId,
                       String description, Instant createdAt, String reversedType) {
        this.userId = userId;
        this.month = month;
        this.amountCents = amountCents;
        this.type = type;
        this.splitId = splitId;
        this.refEntryId = refEntryId;
        this.counterpartyId = counterpartyId;
        this.description = description;
        this.createdAt = createdAt;
        this.reversedType = reversedType;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getMonth() {
        return month;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Type getType() {
        return type;
    }

    public Long getSplitId() {
        return splitId;
    }

    public Long getRefEntryId() {
        return refEntryId;
    }

    public String getReversedType() {
        return reversedType;
    }

    public Long getCounterpartyId() {
        return counterpartyId;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

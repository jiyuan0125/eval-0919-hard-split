package com.ledger.split;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** Immutable per-bill participant share, computed with {@link com.ledger.common.Money#allocate}. */
@Entity
@Table(name = "split_participant",
        uniqueConstraints = @UniqueConstraint(name = "uk_participant_bill_user",
                columnNames = {"bill_id", "user_id"}),
        indexes = @Index(name = "idx_participant_bill", columnList = "bill_id"))
public class SplitParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private Long billId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "share_cents", nullable = false, updatable = false)
    private long shareCents;

    protected SplitParticipant() {
    }

    public SplitParticipant(Long billId, Long userId, long shareCents) {
        this.billId = billId;
        this.userId = userId;
        this.shareCents = shareCents;
    }

    public Long getId() {
        return id;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getUserId() {
        return userId;
    }

    public long getShareCents() {
        return shareCents;
    }
}

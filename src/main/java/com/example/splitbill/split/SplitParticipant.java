package com.example.splitbill.split;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

@Entity
public class SplitParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bill_id")
    private SplitBill bill;

    private Long userId;

    /**
     * Mutable while PENDING (recomputed when someone withdraws), frozen forever
     * once CONFIRMED.
     */
    private long shareCents;

    @Enumerated(EnumType.STRING)
    private ParticipantStatus status = ParticipantStatus.PENDING;

    private Instant confirmedAt;

    protected SplitParticipant() {
    }

    public SplitParticipant(Long userId, long shareCents) {
        this.userId = userId;
        this.shareCents = shareCents;
    }

    public Long getId() {
        return id;
    }

    public SplitBill getBill() {
        return bill;
    }

    void setBill(SplitBill bill) {
        this.bill = bill;
    }

    public Long getUserId() {
        return userId;
    }

    public long getShareCents() {
        return shareCents;
    }

    public void setShareCents(long shareCents) {
        this.shareCents = shareCents;
    }

    public ParticipantStatus getStatus() {
        return status;
    }

    public void setStatus(ParticipantStatus status) {
        this.status = status;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(Instant confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}

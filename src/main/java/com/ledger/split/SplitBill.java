package com.ledger.split;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A shared-expense bill. The row is immutable after insert: lifecycle state is
 * derived from append-only {@link SplitEvent} rows, never from an updated
 * status column.
 */
@Entity
@Table(name = "split_bill", indexes = {
        @Index(name = "idx_bill_predecessor", columnList = "predecessor_id")
})
public class SplitBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payer_id", nullable = false, updatable = false)
    private Long payerId;

    @Column(name = "total_cents", nullable = false, updatable = false)
    private long totalCents;

    @Column(name = "month_key", nullable = false, updatable = false)
    private String month;

    @Column(updatable = false)
    private String description;

    @Column(name = "predecessor_id", updatable = false)
    private Long predecessorId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SplitBill() {
    }

    public SplitBill(Long payerId, long totalCents, String month, String description,
                     Long predecessorId, Instant createdAt) {
        this.payerId = payerId;
        this.totalCents = totalCents;
        this.month = month;
        this.description = description;
        this.predecessorId = predecessorId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getPayerId() {
        return payerId;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public String getMonth() {
        return month;
    }

    public String getDescription() {
        return description;
    }

    public Long getPredecessorId() {
        return predecessorId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

package com.ledger.split;

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
 * Append-only event. Nothing in the split module is ever mutated; every state
 * transition (including mid-bill withdrawal and post-finalize reversal) is a new
 * row in this table.
 */
@Entity
@Table(name = "split_event", indexes = @Index(name = "idx_event_bill", columnList = "bill_id"))
public class SplitEvent {

    public enum Type {
        CREATED,
        CONFIRMED,
        VOIDED,
        FINALIZED,
        REVERSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private Long billId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 24)
    private Type type;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private Long actorId;

    @Column(updatable = false, length = 64)
    private String detail;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SplitEvent() {
    }

    public SplitEvent(Long billId, Type type, Long actorId, String detail, Instant createdAt) {
        this.billId = billId;
        this.type = type;
        this.actorId = actorId;
        this.detail = detail;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBillId() {
        return billId;
    }

    public Type getType() {
        return type;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getDetail() {
        return detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

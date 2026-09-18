package com.ledger.split;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/** Immutable record of one participant settling their whole share with the payer. */
@Entity
@Table(name = "settlement",
        uniqueConstraints = @UniqueConstraint(name = "uk_settlement_bill_debtor",
                columnNames = {"bill_id", "debtor_id"}),
        indexes = @Index(name = "idx_settlement_payer", columnList = "payer_id"))
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bill_id", nullable = false, updatable = false)
    private Long billId;

    @Column(name = "payer_id", nullable = false, updatable = false)
    private Long payerId;

    @Column(name = "debtor_id", nullable = false, updatable = false)
    private Long debtorId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    private long amountCents;

    @Column(name = "settled_at", nullable = false, updatable = false)
    private Instant settledAt;

    protected Settlement() {
    }

    public Settlement(Long billId, Long payerId, Long debtorId, long amountCents, Instant settledAt) {
        this.billId = billId;
        this.payerId = payerId;
        this.debtorId = debtorId;
        this.amountCents = amountCents;
        this.settledAt = settledAt;
    }

    public Long getId() {
        return id;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getPayerId() {
        return payerId;
    }

    public Long getDebtorId() {
        return debtorId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}

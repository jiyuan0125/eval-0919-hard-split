package com.example.splitbill.split;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class SplitBill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long payerId;

    private String title;

    private long totalCents;

    /**
     * The payer's own share, fixed at creation time as floor(total / (friends + 1)).
     * It never changes afterwards; rounding remainders are carried by friends.
     */
    private long payerShareCents;

    @Enumerated(EnumType.STRING)
    private BillStatus status = BillStatus.OPEN;

    private Instant createdAt = Instant.now();

    @OneToMany(mappedBy = "bill", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SplitParticipant> participants = new ArrayList<>();

    protected SplitBill() {
    }

    public SplitBill(Long payerId, String title, long totalCents, long payerShareCents) {
        this.payerId = payerId;
        this.title = title;
        this.totalCents = totalCents;
        this.payerShareCents = payerShareCents;
    }

    public void addParticipant(SplitParticipant participant) {
        participants.add(participant);
        participant.setBill(this);
    }

    public Long getId() {
        return id;
    }

    public Long getPayerId() {
        return payerId;
    }

    public String getTitle() {
        return title;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public long getPayerShareCents() {
        return payerShareCents;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<SplitParticipant> getParticipants() {
        return participants;
    }
}

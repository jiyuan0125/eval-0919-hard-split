package com.ledger.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "monthly_goal", uniqueConstraints = @UniqueConstraint(
        name = "uk_goal_user_month", columnNames = {"user_id", "month_key"}))
public class MonthlyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "month_key", nullable = false, updatable = false)
    private String month;

    @Column(name = "target_cents", nullable = false)
    private long targetCents;

    protected MonthlyGoal() {
    }

    public MonthlyGoal(Long userId, String month, long targetCents) {
        this.userId = userId;
        this.month = month;
        this.targetCents = targetCents;
    }

    public void updateTarget(long targetCents) {
        this.targetCents = targetCents;
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

    public long getTargetCents() {
        return targetCents;
    }
}

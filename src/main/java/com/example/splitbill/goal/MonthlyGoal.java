package com.example.splitbill.goal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.YearMonth;

@Entity
public class MonthlyGoal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    /** ISO format, e.g. "2026-09". */
    private String yearMonth;

    private long targetCents;

    protected MonthlyGoal() {
    }

    public MonthlyGoal(Long userId, YearMonth yearMonth, long targetCents) {
        this.userId = userId;
        this.yearMonth = yearMonth.toString();
        this.targetCents = targetCents;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public YearMonth getYearMonth() {
        return YearMonth.parse(yearMonth);
    }

    public long getTargetCents() {
        return targetCents;
    }
}

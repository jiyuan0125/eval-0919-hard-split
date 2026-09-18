package com.ledger.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GoalRepository extends JpaRepository<MonthlyGoal, Long> {
    Optional<MonthlyGoal> findByUserIdAndMonth(Long userId, String month);
}

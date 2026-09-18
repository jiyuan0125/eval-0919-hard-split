package com.example.splitbill.goal;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<MonthlyGoal, Long> {
}

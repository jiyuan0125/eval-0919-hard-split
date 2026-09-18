package com.example.splitbill.goal;

import com.example.splitbill.common.DomainException;
import com.example.splitbill.ledger.LedgerService;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final LedgerService ledgerService;

    public GoalService(GoalRepository goalRepository, LedgerService ledgerService) {
        this.goalRepository = goalRepository;
        this.ledgerService = ledgerService;
    }

    @Transactional
    public MonthlyGoal createGoal(Long userId, YearMonth yearMonth, long targetCents) {
        if (targetCents <= 0) {
            throw DomainException.badRequest("target amount must be positive");
        }
        return goalRepository.save(new MonthlyGoal(userId, yearMonth, targetCents));
    }

    @Transactional(readOnly = true)
    public MonthlyGoal getGoal(Long goalId) {
        return goalRepository.findById(goalId)
                .orElseThrow(() -> DomainException.notFound("goal not found: " + goalId));
    }

    /** Full precision view, only ever returned to the goal owner. */
    @Transactional(readOnly = true)
    public OwnerProgress ownerProgress(MonthlyGoal goal) {
        long spent = spentCents(goal);
        long remaining = Math.max(0, goal.getTargetCents() - spent);
        double percentExact = Math.round(spent * 10000.0 / goal.getTargetCents()) / 100.0;
        return new OwnerProgress(goal.getTargetCents(), spent, remaining, percentExact);
    }

    /**
     * Privacy-preserving view for friends: a single coarse integer percentage,
     * floored and clamped to [0, 100]. No amount fields exist in this view, so
     * neither target nor spent amounts can be reconstructed from it.
     */
    @Transactional(readOnly = true)
    public FriendProgress friendProgress(MonthlyGoal goal) {
        long spent = spentCents(goal);
        long percent = spent * 100 / goal.getTargetCents();
        int clamped = (int) Math.max(0, Math.min(100, percent));
        return new FriendProgress(clamped);
    }

    private long spentCents(MonthlyGoal goal) {
        return ledgerService.sumExpenses(goal.getUserId(), goal.getYearMonth());
    }

    public record OwnerProgress(long targetCents, long spentCents, long remainingCents,
                                double percentExact) {
    }

    public record FriendProgress(int percent) {
    }
}

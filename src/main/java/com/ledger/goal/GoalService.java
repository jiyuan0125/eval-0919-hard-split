package com.ledger.goal;

import com.ledger.common.ApiException;
import com.ledger.common.ErrorCode;
import com.ledger.ledger.LedgerService;
import com.ledger.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.YearMonth;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final LedgerService ledgerService;
    private final UserService userService;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository, LedgerService ledgerService,
                       UserService userService, Clock clock) {
        this.goalRepository = goalRepository;
        this.ledgerService = ledgerService;
        this.userService = userService;
        this.clock = clock;
    }

    @Transactional
    public MonthlyGoal setGoal(Long userId, Long monthOffset, long targetCents) {
        userService.requireUser(userId);
        if (targetCents <= 0) {
            throw new ApiException(ErrorCode.VALIDATION, "goal target must be positive");
        }
        YearMonth month = resolveMonth(monthOffset);
        MonthlyGoal goal = goalRepository.findByUserIdAndMonth(userId, month.toString())
                .orElseGet(() -> goalRepository.save(new MonthlyGoal(userId, month.toString(), targetCents)));
        goal.updateTarget(targetCents);
        return goalRepository.saveAndFlush(goal);
    }

    /**
     * Self view: exact amounts.
     */
    @Transactional(readOnly = true)
    public SelfProgressView selfProgress(Long userId, Long monthOffset) {
        userService.requireUser(userId);
        YearMonth month = resolveMonth(monthOffset);
        MonthlyGoal goal = goalRepository.findByUserIdAndMonth(userId, month.toString())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "no monthly goal set for " + month));
        long spent = ledgerService.spentInMonth(userId, month);
        return new SelfProgressView(userId, month.toString(), goal.getTargetCents(), spent);
    }

    /**
     * Friend view: ONLY an integer percentage 0..100. No target, no spent, no
     * remaining, no fraction - the friend cannot combine responses to recover
     * any amount.
     */
    @Transactional(readOnly = true)
    public FriendProgressView friendProgress(Long viewerId, Long targetUserId, Long monthOffset) {
        userService.requireFriendship(viewerId, targetUserId);
        YearMonth month = resolveMonth(monthOffset);
        MonthlyGoal goal = goalRepository.findByUserIdAndMonth(targetUserId, month.toString())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "no goal data for that month"));
        long spent = ledgerService.spentInMonth(targetUserId, month);
        int percent = (int) Math.min(100, Math.max(0, spent * 100 / goal.getTargetCents()));
        return new FriendProgressView(targetUserId, month.toString(), percent);
    }

    private YearMonth resolveMonth(Long monthOffset) {
        YearMonth now = YearMonth.now(clock);
        return monthOffset == null ? now : now.plusMonths(monthOffset);
    }
}

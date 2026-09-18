package com.ledger.goal;

import com.ledger.common.Money;
import com.ledger.user.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @PostMapping("/me")
    public Map<String, Object> setGoal(@CurrentUser Long userId,
                                       @Valid @RequestBody SetGoalRequest request) {
        long cents = Money.yuanToCents(request.target());
        MonthlyGoal goal = goalService.setGoal(userId, request.monthOffset(), cents);
        return Map.of("userId", goal.getUserId(), "month", goal.getMonth(),
                "target", Money.centsToYuan(goal.getTargetCents()));
    }

    /** Exact numbers - owner only. */
    @GetMapping("/me/progress")
    public SelfProgressResponse myProgress(@CurrentUser Long userId,
                                           @RequestParam(name = "monthOffset", required = false) Long monthOffset) {
        SelfProgressView view = goalService.selfProgress(userId, monthOffset);
        return new SelfProgressResponse(view.userId(), view.month(), view.target(), view.spent(),
                view.remaining(), view.percentPrecise());
    }

    /** Friends only ever receive this percent-only payload. */
    @GetMapping("/users/{userId}/progress")
    public FriendProgressResponse friendProgress(@CurrentUser Long viewerId,
                                                 @PathVariable Long userId,
                                                 @RequestParam(name = "monthOffset", required = false) Long monthOffset) {
        FriendProgressView view = goalService.friendProgress(viewerId, userId, monthOffset);
        return new FriendProgressResponse(view.userId(), view.month(), view.percent());
    }

    public record SetGoalRequest(@NotBlank String target, Long monthOffset) {
    }

    public record SelfProgressResponse(long userId, String month, String target, String spent,
                                       String remaining, String percentPrecise) {
    }

    /** Field set is intentionally minimal: percent is the only progress signal. */
    public record FriendProgressResponse(long userId, String month, int percent) {
    }
}

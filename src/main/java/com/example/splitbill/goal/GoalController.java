package com.example.splitbill.goal;

import com.example.splitbill.common.DomainException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.YearMonth;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/goals")
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    public record CreateGoalRequest(@NotNull Long userId,
                                    @Pattern(regexp = "\\d{4}-\\d{2}") String yearMonth,
                                    @Positive long targetCents) {
    }

    public record GoalView(Long id, Long userId, String yearMonth, long targetCents) {
        static GoalView of(MonthlyGoal goal) {
            return new GoalView(goal.getId(), goal.getUserId(),
                    goal.getYearMonth().toString(), goal.getTargetCents());
        }
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GoalView create(@Valid @RequestBody CreateGoalRequest request) {
        return GoalView.of(goalService.createGoal(
                request.userId(), YearMonth.parse(request.yearMonth()), request.targetCents()));
    }

    /**
     * Owner (viewerId == goal owner) gets exact amounts; anyone else gets the
     * coarse friend view with an integer percentage only.
     */
    @GetMapping("/{goalId}/progress")
    public ResponseEntity<?> progress(@PathVariable Long goalId, @RequestParam Long viewerId) {
        MonthlyGoal goal = goalService.getGoal(goalId);
        if (goal.getUserId().equals(viewerId)) {
            return ResponseEntity.ok(goalService.ownerProgress(goal));
        }
        return ResponseEntity.ok(goalService.friendProgress(goal));
    }
}

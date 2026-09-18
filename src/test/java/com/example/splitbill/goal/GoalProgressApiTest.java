package com.example.splitbill.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.splitbill.ledger.LedgerService;
import com.example.splitbill.user.AppUser;
import com.example.splitbill.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GoalProgressApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    GoalService goalService;
    @Autowired
    LedgerService ledgerService;
    @Autowired
    UserRepository userRepository;

    Long owner;
    Long friend;

    @BeforeEach
    void setUp() {
        owner = userRepository.save(new AppUser("owner")).getId();
        friend = userRepository.save(new AppUser("friend")).getId();
    }

    private MonthlyGoal goalWith(long targetCents, long spentCents) {
        // Each goal gets its own owner: expenses are aggregated per user and
        // month, so goals sharing an owner would also share the spend total.
        Long goalOwner = userRepository.save(new AppUser("owner-" + targetCents + "-" + spentCents)).getId();
        MonthlyGoal goal = goalService.createGoal(goalOwner, YearMonth.of(2026, 9), targetCents);
        if (spentCents > 0) {
            ledgerService.recordExpense(goalOwner, spentCents, LocalDate.of(2026, 9, 10), "spend");
        }
        return goal;
    }

    private JsonNode progressJson(Long goalId, Long viewerId) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/goals/{id}/progress", goalId)
                        .param("viewerId", viewerId.toString()))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void ownerSeesExactAmounts() throws Exception {
        MonthlyGoal goal = goalWith(200000, 50505);

        mockMvc.perform(get("/api/goals/{id}/progress", goal.getId())
                        .param("viewerId", goal.getUserId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetCents").value(200000))
                .andExpect(jsonPath("$.spentCents").value(50505))
                .andExpect(jsonPath("$.remainingCents").value(149495))
                .andExpect(jsonPath("$.percentExact").value(25.25));
    }

    @Test
    void friendSeesOnlyCoarseIntegerPercent() throws Exception {
        Long goalId = goalWith(200000, 50505).getId();

        JsonNode json = progressJson(goalId, friend);

        assertThat(json.fieldNames()).toIterable().containsExactly("percent");
        assertThat(json.get("percent").asInt()).isEqualTo(25);
    }

    @Test
    void friendViewCannotBeUsedToReverseEngineerAmounts() throws Exception {
        // Two goals with completely different amounts but the same integer
        // percentage must produce byte-identical friend responses.
        Long smallGoal = goalWith(10000, 2500).getId();      // 100.00 target, 25% spent
        Long largeGoal = goalWith(98765400, 24691350).getId(); // 987,654.00 target, 25% spent

        JsonNode small = progressJson(smallGoal, friend);
        JsonNode large = progressJson(largeGoal, friend);

        assertThat(small).isEqualTo(large);
        assertThat(small.toString()).isEqualTo("{\"percent\":25}");
    }

    @Test
    void friendViewIsStableWithinSamePercentBucket() throws Exception {
        // Spending more inside the same integer-percent bucket must not leak
        // through the friend view, so no amount delta can be inferred either.
        MonthlyGoal goal = goalWith(100000, 25000);
        JsonNode before = progressJson(goal.getId(), friend);

        ledgerService.recordExpense(goal.getUserId(), 900, LocalDate.of(2026, 9, 11), "more");
        JsonNode after = progressJson(goal.getId(), friend);

        assertThat(after).isEqualTo(before);
        // ...while the owner does see the difference
        JsonNode ownerView = progressJson(goal.getId(), goal.getUserId());
        assertThat(ownerView.get("spentCents").asLong()).isEqualTo(25900);
    }

    @Test
    void friendPercentIsFlooredAndClamped() throws Exception {
        assertThat(progressJson(goalWith(30000, 29999).getId(), friend).get("percent").asInt())
                .isEqualTo(99); // 99.996% floors to 99, never rounds up to 100
        assertThat(progressJson(goalWith(10000, 25000).getId(), friend).get("percent").asInt())
                .isEqualTo(100); // overspend clamps at 100
        assertThat(progressJson(goalWith(10000, 0).getId(), friend).get("percent").asInt())
                .isEqualTo(0);
    }

    @Test
    void createGoalViaApi() throws Exception {
        mockMvc.perform(post("/api/goals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId": %d, "yearMonth": "2026-09", "targetCents": 50000}
                                """.formatted(owner)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(owner))
                .andExpect(jsonPath("$.targetCents").value(50000));
    }

    @Test
    void unknownGoalReturns404() throws Exception {
        mockMvc.perform(get("/api/goals/{id}/progress", 424242)
                        .param("viewerId", friend.toString()))
                .andExpect(status().isNotFound());
    }
}

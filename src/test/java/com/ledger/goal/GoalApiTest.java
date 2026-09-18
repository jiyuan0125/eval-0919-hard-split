package com.ledger.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GoalApiTest extends ApiTestBase {

    @Test
    void setsGoalAndTracksProgress() throws Exception {
        long alice = createUser("alice-goal");
        JsonNode goal = postJson("/api/goals/me", alice, body("target", "500.00"));
        assertThat(goal.get("month").asText()).isEqualTo("2026-05");

        postJson("/api/ledger/entries", alice, body("amount", "100.25", "description", "a"));
        postJson("/api/ledger/entries", alice, body("amount", "49.75", "description", "b"));

        JsonNode progress = getJson("/api/goals/me/progress", alice);
        assertThat(progress.get("spent").asText()).isEqualTo("150.00");
        assertThat(progress.get("remaining").asText()).isEqualTo("350.00");
        assertThat(progress.get("percentPrecise").asText()).isEqualTo("30.00");
    }

    @Test
    void progressCapsAtOneHundredPercent() throws Exception {
        long alice = createUser("alice-goal-cap");
        postJson("/api/goals/me", alice, body("target", "10.00"));
        postJson("/api/ledger/entries", alice, body("amount", "50.00"));

        assertThat(getJson("/api/goals/me/progress", alice).get("percentPrecise").asText())
                .isEqualTo("100.00");

        long bob = createUser("bob-goal-cap");
        addFriend(bob, alice);
        assertThat(getJson("/api/goals/users/" + alice + "/progress", bob).get("percent").asInt())
                .isEqualTo(100);
    }

    @Test
    void resetsGoalWithUpsertAndValidatesTarget() throws Exception {
        long alice = createUser("alice-goal-reset");
        postJson("/api/goals/me", alice, body("target", "100.00"));
        JsonNode updated = postJson("/api/goals/me", alice, body("target", "200.00"));
        assertThat(updated.get("target").asText()).isEqualTo("200.00");

        assertThat(postJsonExpect("/api/goals/me", alice, body("target", "0"), 400)
                .get("error").asText()).isEqualTo("VALIDATION");
    }

    @Test
    void progressWithoutGoalIsNotFound() throws Exception {
        long alice = createUser("alice-goal-none");
        getExpect("/api/goals/me/progress", alice, 404);
        long bob = createUser("bob-goal-none");
        addFriend(bob, alice);
        getExpect("/api/goals/users/" + alice + "/progress", bob, 404);
    }
}

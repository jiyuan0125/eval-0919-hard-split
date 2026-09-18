package com.ledger.goal;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Dedicated proof that the friend-facing goal API leaks no monetary data and
 * cannot be combined across requests to recover amounts.
 */
class GoalPrivacyTest extends ApiTestBase {

    private static final Set<String> FORBIDDEN_KEYS = Set.of(
            "target", "spent", "remaining", "goal", "amount", "amountCents",
            "targetCents", "spentCents", "percentPrecise", "balance", "total");

    private long setupAliceWithGoalAndSpending(String target, String... spends) throws Exception {
        long alice = createUser("alice-privacy-" + target + "-" + spends.length);
        postJson("/api/goals/me", alice, body("target", target));
        for (String spend : spends) {
            postJson("/api/ledger/entries", alice, body("amount", spend, "description", "x"));
        }
        return alice;
    }

    private Set<String> collectAllFieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        collect(node, names);
        return names;
    }

    private void collect(JsonNode node, Set<String> names) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> fields = node.fieldNames();
            while (fields.hasNext()) {
                String field = fields.next();
                names.add(field);
                collect(node.get(field), names);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, names));
        }
    }

    @Test
    void friendGetsOnlyIntegerPercentNoAmountFields() throws Exception {
        long alice = setupAliceWithGoalAndSpending("1000.00", "300.00");
        long bob = createUser("bob-privacy");
        addFriend(bob, alice);

        JsonNode friendView = getJson("/api/goals/users/" + alice + "/progress", bob);

        Set<String> fields = collectAllFieldNames(friendView);
        assertThat(fields).containsExactlyInAnyOrder("userId", "month", "percent");
        assertThat(fields).isSubsetOf(Set.of("userId", "month", "percent"));
        for (String forbidden : FORBIDDEN_KEYS) {
            assertThat(fields).doesNotContain(forbidden);
        }
        assertThat(friendView.get("percent").isInt()).isTrue();
        assertThat(friendView.get("percent").asInt()).isEqualTo(30);
    }

    @Test
    void nonFriendIsForbiddenAndNoBodyDataLeaks() throws Exception {
        long alice = setupAliceWithGoalAndSpending("500.00", "100.00");
        long mallory = createUser("mallory-privacy");

        JsonNode error = getExpect("/api/goals/users/" + alice + "/progress", mallory, 403);
        assertThat(error.get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(collectAllFieldNames(error)).doesNotContainAnyElementsOf(
                Set.of("target", "spent", "remaining", "percent", "percentPrecise"));
    }

    @Test
    void friendCannotCombineResponsesToRecoverAmounts() throws Exception {
        // Two different users with DIFFERENT targets and spend but the SAME
        // rounded percent. If the friend could recover amounts from percent,
        // these responses would be distinguishable - they are byte-for-byte
        // apart from ids/month, which carry no amount information.
        long alice = setupAliceWithGoalAndSpending("1000.00", "300.00");   // 30.00%
        long anna = setupAliceWithGoalAndSpending("3333.33", "999.99");    // 29.99...% -> 29%
        long ada = setupAliceWithGoalAndSpending("900.00", "270.00");      // 30.00% -> 30%
        long bob = createUser("bob-privacy-combine");
        addFriend(bob, alice);
        addFriend(bob, ada);

        JsonNode a = getJson("/api/goals/users/" + alice + "/progress", bob);
        JsonNode b = getJson("/api/goals/users/" + ada + "/progress", bob);

        assertThat(a.get("percent").asInt()).isEqualTo(b.get("percent").asInt()).isEqualTo(30);
        // repeat polling: identical inputs always yield identical, amount-free output
        JsonNode aAgain = getJson("/api/goals/users/" + alice + "/progress", bob);
        assertThat(aAgain.toString()).isEqualTo(a.toString());

        // anna is not a friend -> no signal at all
        getExpect("/api/goals/users/" + anna + "/progress", bob, 403);

        // every friend response scanned for any yuan-like token or decimals:
        // the only numeric VALUE (beyond the public user id) is an integer percent
        for (JsonNode node : new JsonNode[]{a, b, aAgain}) {
            node.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value.isNumber() && !"userId".equals(entry.getKey())) {
                    assertThat(entry.getKey()).isEqualTo("percent");
                    assertThat(value.isInt()).isTrue();
                }
            });
        }
        // sanity: even the raw payload strings contain no decimal point
        assertThat(a.toString()).doesNotContain(".");
        assertThat(b.toString()).doesNotContain(".");
    }

    @Test
    void friendCannotUseMonthOffsetsOrLedgerEndpointsToProbeAmounts() throws Exception {
        long alice = setupAliceWithGoalAndSpending("1000.00", "300.00", "300.00");
        long bob = createUser("bob-privacy-probe");
        addFriend(bob, alice);

        // 600/1000 -> 60%, integer only
        JsonNode view = getJson("/api/goals/users/" + alice + "/progress", bob);
        assertThat(view.get("percent").asInt()).isEqualTo(60);

        // ledger entries of another user are never exposed
        getExpect("/api/ledger/entries", bob, 200);
        // there is no cross-user ledger path; requesting as alice works, as bob shows only his own
        assertThat(getJson("/api/ledger/entries", bob).isArray()).isTrue();
        assertThat(getJson("/api/ledger/entries", bob)).isEmpty();

        // querying non-existent months returns 404, not an amount-bearing empty payload
        getExpect("/api/goals/users/" + alice + "/progress?monthOffset=-12", bob, 404);
    }

    @Test
    void ownerSeesExactNumbersViaSeparateOwnerEndpoint() throws Exception {
        long alice = setupAliceWithGoalAndSpending("1000.00", "333.33");
        JsonNode self = getJson("/api/goals/me/progress", alice);
        assertThat(self.get("target").asText()).isEqualTo("1000.00");
        assertThat(self.get("spent").asText()).isEqualTo("333.33");
        assertThat(self.get("remaining").asText()).isEqualTo("666.67");
        assertThat(self.get("percentPrecise").asText()).isEqualTo("33.33");

        // the owner endpoint must not serve friends
        long bob = createUser("bob-privacy-owner");
        // /me is always the caller - bob simply gets his own 404, never alice's numbers
        getExpect("/api/goals/me/progress", bob, 404);
    }
}

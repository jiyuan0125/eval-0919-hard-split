package com.ledger.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the full happy path (create -> confirm -> finalize -> settle -> reverse)
 * with the Hibernate statement inspector armed: any UPDATE/DELETE against
 * split_bill / split_participant / split_event / settlement / ledger_entry
 * would abort the request.
 */
class ImmutabilityGuardTest extends ApiTestBase {

    @BeforeAll
    static void armGuard() {
        ImmutableTablesInspector.enabled = true;
    }

    @BeforeEach
    void rearmGuard() {
        ImmutableTablesInspector.enabled = true;
    }

    @Test
    void fullLifecycleIssuesOnlyInsertsOnGuardedTables() throws Exception {
        assertThat(ImmutableTablesInspector.enabled).isTrue();

        long payer = createUser("payer-guard");
        long u2 = createUser("u2-guard");
        long u3 = createUser("u3-guard");

        JsonNode bill = postJson("/api/splits", payer,
                body("total", "100.00", "participantIds", List.of(u2, u3)));
        long billId = bill.get("id").asLong();
        postJson("/api/splits/" + billId + "/confirm", u2);
        postJson("/api/splits/" + billId + "/confirm", u3);
        postJson("/api/splits/" + billId + "/settle", u2);
        postJson("/api/splits/" + billId + "/reverse", payer, body("reason", "audit"));

        assertThat(getJson("/api/splits/" + billId, payer).get("status").asText())
                .isEqualTo("REVERSED");
    }

    @Test
    void withdrawalAndCancellationAlsoOnlyAppend() throws Exception {
        long payer = createUser("payer-guard2");
        long u2 = createUser("u2-guard2");
        long u3 = createUser("u3-guard2");

        long billId = postJson("/api/splits", payer,
                body("total", "100.00", "participantIds", List.of(u2, u3))).get("id").asLong();
        postJson("/api/splits/" + billId + "/confirm", u2);
        long successor = postJson("/api/splits/" + billId + "/withdraw", u3, body())
                .get("id").asLong();
        postJson("/api/splits/" + successor + "/cancel", payer, body());

        assertThat(getJson("/api/splits/" + billId, payer).get("status").asText())
                .isEqualTo("VOIDED");
        assertThat(getJson("/api/splits/" + successor, payer).get("status").asText())
                .isEqualTo("VOIDED");
    }

    @Test
    void inspectorIgnoresInsertsAndUpdatesOfOtherTables() {
        ImmutableTablesInspector inspector = new ImmutableTablesInspector();
        boolean previous = ImmutableTablesInspector.enabled;
        ImmutableTablesInspector.enabled = true;
        try {
            assertThat(inspector.inspect("insert into split_event (id) values (1)"))
                    .contains("insert");
            assertThat(inspector.inspect("update monthly_goal set target_cents = 5"))
                    .contains("update");
        } finally {
            ImmutableTablesInspector.enabled = previous;
        }
    }

    @Test
    void inspectorThrowsOnGuardedMutationAndStaysSilentWhenDisabled() {
        ImmutableTablesInspector inspector = new ImmutableTablesInspector();
        boolean previous = ImmutableTablesInspector.enabled;
        ImmutableTablesInspector.enabled = true;
        try {
            try {
                inspector.inspect("update split_bill set payer_id = 2 where id = 1");
                throw new AssertionError("expected ImmutableWriteAttemptException");
            } catch (ImmutableWriteAttemptException expected) {
                // expected
            }
            try {
                inspector.inspect("delete from ledger_entry where id = 1");
                throw new AssertionError("expected ImmutableWriteAttemptException");
            } catch (ImmutableWriteAttemptException expected) {
                // expected
            }
        } finally {
            ImmutableTablesInspector.enabled = previous;
        }
        ImmutableTablesInspector.enabled = false;
        // disabled: passes through untouched
        inspector.inspect("update split_bill set payer_id = 2");
    }
}

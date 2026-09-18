package com.ledger.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerApiTest extends ApiTestBase {

    @Test
    void booksManualExpenseForCurrentMonth() throws Exception {
        long alice = createUser("alice-ledger");

        JsonNode created = postJson("/api/ledger/entries", alice,
                body("amount", "12.50", "description", "lunch"));
        assertThat(created.get("amount").asText()).isEqualTo("12.50");
        assertThat(created.get("type").asText()).isEqualTo("MANUAL_EXPENSE");
        assertThat(created.get("month").asText()).isEqualTo("2026-05");

        JsonNode list = getJson("/api/ledger/entries?month=2026-05", alice);
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("description").asText()).isEqualTo("lunch");
    }

    @Test
    void rejectsBadAmounts() throws Exception {
        long alice = createUser("alice-ledger2");
        assertThat(postJsonExpect("/api/ledger/entries", alice, body("amount", "nope"), 400)
                .get("error").asText()).isEqualTo("VALIDATION");
        assertThat(postJsonExpect("/api/ledger/entries", alice, body("amount", "1.234"), 400)
                .get("error").asText()).isEqualTo("VALIDATION");
    }

    @Test
    void monthWithoutEntriesReturnsEmptyList() throws Exception {
        long alice = createUser("alice-ledger3");
        assertThat(getJson("/api/ledger/entries?month=2026-04", alice)).isEmpty();
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        long alice = createUser("alice-ledger4");
        postRawExpect("/api/ledger/entries", alice, "{not-json", 400);
    }
}

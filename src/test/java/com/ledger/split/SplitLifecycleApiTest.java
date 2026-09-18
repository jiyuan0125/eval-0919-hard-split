package com.ledger.split;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplitLifecycleApiTest extends ApiTestBase {

    private long[] dinnerCrew() throws Exception {
        long payer = createUser("payer-split");
        long u2 = createUser("u2-split");
        long u3 = createUser("u3-split");
        return new long[]{payer, u2, u3};
    }

    @Test
    void createsBillWithDeterministicShares() throws Exception {
        long[] u = dinnerCrew();
        JsonNode bill = postJson("/api/splits", u[0],
                body("total", "100.00", "participantIds", java.util.List.of(u[1], u[2]),
                        "description", "dinner"));

        assertThat(bill.get("status").asText()).isEqualTo("INITIATED");
        assertThat(bill.get("total").asText()).isEqualTo("100.00");
        JsonNode participants = bill.get("participants");
        assertThat(participants).hasSize(3);
        // lowest user id absorbs the extra cent; ids are assigned sequentially so
        // payer is the smallest, hence 33.34 / 33.33 / 33.33
        assertThat(participants.get(0).get("share").asText()).isEqualTo("33.34");
        assertThat(participants.get(1).get("share").asText()).isEqualTo("33.33");
        assertThat(participants.get(2).get("share").asText()).isEqualTo("33.33");
    }

    @Test
    void confirmationsFinalizeAndBookLedgerEntries() throws Exception {
        long[] u = dinnerCrew();
        long billId = postJson("/api/splits", u[0],
                body("total", "99.99", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();

        postJson("/api/splits/" + billId + "/confirm", u[1]);
        JsonNode afterFirst = getJson("/api/splits/" + billId, u[0]);
        assertThat(afterFirst.get("status").asText()).isEqualTo("INITIATED");

        JsonNode finalized = postJson("/api/splits/" + billId + "/confirm", u[2]);
        assertThat(finalized.get("status").asText()).isEqualTo("FINALIZED");

        JsonNode payerLedger = getJson("/api/ledger/entries", u[0]);
        assertThat(payerLedger).extracting(n -> n.get("type").asText())
                .containsOnlyOnce("SPLIT_RECEIVABLE");

        JsonNode debtorLedger = getJson("/api/ledger/entries", u[1]);
        assertThat(debtorLedger.get(0).get("type").asText()).isEqualTo("SPLIT_EXPENSE");
    }

    @Test
    void confirmationIsIdempotentAndRejectsNonParticipants() throws Exception {
        long[] u = dinnerCrew();
        long billId = postJson("/api/splits", u[0],
                body("total", "10", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();

        postJson("/api/splits/" + billId + "/confirm", u[1]);
        postJson("/api/splits/" + billId + "/confirm", u[1]);
        JsonNode bill = getJson("/api/splits/" + billId, u[1]);
        assertThat(bill.get("confirmedUserIds")).hasSize(1);

        long outsider = createUser("outsider-split");
        assertThat(postJsonExpect("/api/splits/" + billId + "/confirm", outsider, body(), 403)
                .get("error").asText()).isEqualTo("FORBIDDEN");
        assertThat(getExpect("/api/splits/" + billId, outsider, 403).get("error").asText())
                .isEqualTo("FORBIDDEN");
    }

    @Test
    void payerCannotConfirmAndBadBillsAreRejected() throws Exception {
        long[] u = dinnerCrew();
        long billId = postJson("/api/splits", u[0],
                body("total", "10", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();
        assertThat(postJsonExpect("/api/splits/" + billId + "/confirm", u[0], body(), 400)
                .get("message").asText()).contains("payer");

        postJsonExpect("/api/splits", u[0],
                body("total", "0", "participantIds", java.util.List.of(u[1])), 400);
        postJsonExpect("/api/splits", u[0],
                body("total", "10", "participantIds", java.util.List.of()), 400);
        postJsonExpect("/api/splits", u[0],
                body("total", "10", "participantIds", java.util.List.of(u[1], u[1])), 400);
        postJsonExpect("/api/splits", 999999L,
                body("total", "10", "participantIds", java.util.List.of(u[1])), 404);
    }

    @Test
    void midBillWithdrawalVoidsOldBillAndCreatesSuccessorForReconfirmation() throws Exception {
        long[] u = dinnerCrew();
        long billId = postJson("/api/splits", u[0],
                body("total", "100.00", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();
        postJson("/api/splits/" + billId + "/confirm", u[1]);

        JsonNode successor = postJson("/api/splits/" + billId + "/withdraw", u[2],
                body("reason", "not eating"));

        // old bill is voided, immutable, and links to nothing in its own ledger
        JsonNode oldBill = getJson("/api/splits/" + billId, u[0]);
        assertThat(oldBill.get("status").asText()).isEqualTo("VOIDED");
        assertThat(successor.get("predecessorId").asLong()).isEqualTo(billId);
        assertThat(successor.get("status").asText()).isEqualTo("INITIATED");
        assertThat(successor.get("confirmedUserIds")).isEmpty();
        assertThat(successor.get("participants")).hasSize(2);
        assertThat(successor.get("participants").get(0).get("share").asText()).isEqualTo("50.00");

        // nobody's ledger was touched before finalization
        assertThat(getJson("/api/ledger/entries", u[1])).isEmpty();

        // cannot confirm the voided bill anymore
        assertThat(postJsonExpect("/api/splits/" + billId + "/confirm", u[1], body(), 409)
                .get("error").asText()).isEqualTo("CONFLICT");
        // payer withdraws by cancelling instead
        assertThat(postJsonExpect("/api/splits/" + successor.get("id").asLong() + "/withdraw",
                u[0], body(), 400).get("error").asText()).isEqualTo("VALIDATION");
    }

    @Test
    void payerCancelsInitiatedBill() throws Exception {
        long[] u = dinnerCrew();
        long billId = postJson("/api/splits", u[0],
                body("total", "100.00", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();

        JsonNode cancelled = postJson("/api/splits/" + billId + "/cancel", u[0], body("reason", "nope"));
        assertThat(cancelled.get("status").asText()).isEqualTo("VOIDED");

        long otherPayer = createUser("other-payer");
        assertThat(postJsonExpect("/api/splits/" + billId + "/cancel", otherPayer, body(), 403)
                .get("error").asText()).isEqualTo("FORBIDDEN");
    }
}

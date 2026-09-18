package com.ledger.split;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SplitSettlementReversalApiTest extends ApiTestBase {

    private long[] crewOfThree() throws Exception {
        long payer = createUser("payer-set");
        long u2 = createUser("u2-set");
        long u3 = createUser("u3-set");
        return new long[]{payer, u2, u3};
    }

    private long finalizedBill(long[] u) throws Exception {
        long billId = postJson("/api/splits", u[0],
                body("total", "100.00", "participantIds", java.util.List.of(u[1], u[2]))).get("id").asLong();
        postJson("/api/splits/" + billId + "/confirm", u[1]);
        postJson("/api/splits/" + billId + "/confirm", u[2]);
        return billId;
    }

    @Test
    void debtorSettlesShareAndOutstandingIsTracked() throws Exception {
        long[] u = crewOfThree();
        long billId = finalizedBill(u);

        JsonNode before = getJson("/api/splits/me/outstanding", u[1]);
        assertThat(before.get("totalOutstanding").asText()).isEqualTo("33.33");

        JsonNode settled = postJson("/api/splits/" + billId + "/settle", u[1]);
        assertThat(settled.get("amount").asText()).isEqualTo("33.33");

        JsonNode after = getJson("/api/splits/me/outstanding", u[1]);
        assertThat(after.get("totalOutstanding").asText()).isEqualTo("0.00");

        // idempotent
        postJson("/api/splits/" + billId + "/settle", u[1]);
        assertThat(getJson("/api/ledger/entries", u[1])).hasSize(2); // expense + payment

        // payer cannot settle their own bill; cannot settle unfinalized
        assertThat(postJsonExpect("/api/splits/" + billId + "/settle", u[0], 400)
                .get("error").asText()).isEqualTo("VALIDATION");
    }

    @Test
    void reversalCreatesNegativeCorrectionLinesWithoutUpdatingHistory() throws Exception {
        long[] u = crewOfThree();
        long billId = finalizedBill(u);
        postJson("/api/splits/" + billId + "/settle", u[1]);

        JsonNode beforeReversal = getJson("/api/splits/" + billId, u[0]);
        int originalEventCount = beforeReversal.get("events").size();
        JsonNode reversed = postJson("/api/splits/" + billId + "/reverse", u[0],
                body("reason", "wrong total"));
        assertThat(reversed.get("status").asText()).isEqualTo("REVERSED");

        // original lines are untouched; corrections are appended REVERSAL rows
        JsonNode payerLines = getJson("/api/ledger/entries", u[0]);
        JsonNode debtorLines = getJson("/api/ledger/entries", u[1]);
        long payerNet = sumSigned(payerLines);
        long debtorNet = sumSigned(debtorLines);
        assertThat(payerNet).isZero();
        assertThat(debtorNet).isZero();

        assertThat(payerLines.findValuesAsText("type")).contains("REVERSAL");
        assertThat(debtorLines.findValuesAsText("type")).contains("REVERSAL");
        // an event was appended only
        assertThat(getJson("/api/splits/" + billId, u[0]).get("events"))
                .hasSize(originalEventCount + 1);

        // a REVERSED bill is inert
        assertThat(postJsonExpect("/api/splits/" + billId + "/settle", u[2], 409)
                .get("error").asText()).isEqualTo("CONFLICT");
        assertThat(postJsonExpect("/api/splits/" + billId + "/reverse", u[0], body(), 409)
                .get("error").asText()).isEqualTo("CONFLICT");
        // only payer reverses
        long other = finalizedBill(new long[]{createUser("p2"), createUser("d2"), createUser("d3")});
        assertThat(postJsonExpect("/api/splits/" + other + "/reverse", u[1], body(), 403)
                .get("error").asText()).isEqualTo("FORBIDDEN");
        // only finalized bills reverse
        long initiated = postJson("/api/splits", u[0],
                body("total", "10", "participantIds", java.util.List.of(u[1]))).get("id").asLong();
        assertThat(postJsonExpect("/api/splits/" + initiated + "/reverse", u[0], body(), 409)
                .get("error").asText()).isEqualTo("CONFLICT");
    }

    private long sumSigned(JsonNode entries) {
        long total = 0;
        for (JsonNode entry : entries) {
            total += new java.math.BigDecimal(entry.get("amount").asText())
                    .movePointRight(2).longValueExact();
        }
        return total;
    }

    @Test
    void reversingBeforeSettlementOnlyReversesExpenseLines() throws Exception {
        long[] u = crewOfThree();
        long billId = finalizedBill(u);
        postJson("/api/splits/" + billId + "/reverse", u[0], body());

        // u2 settled nothing, so net expense must be zero from the reversing pair
        JsonNode u2Lines = getJson("/api/ledger/entries", u[1]);
        assertThat(u2Lines.findValuesAsText("type"))
                .containsExactly("SPLIT_EXPENSE", "REVERSAL");
        assertThat(sumSigned(u2Lines)).isZero();

        // reversed bill excluded from outstanding
        assertThat(getJson("/api/splits/me/outstanding", u[1]).get("totalOutstanding").asText())
                .isEqualTo("0.00");
    }

    @Test
    void reversalRemovesExpenseFromGoalProgressSpent() throws Exception {
        long[] u = crewOfThree();
        long billId = finalizedBill(u);
        postJson("/api/goals/me", u[1], body("target", "100.00"));
        // 33.33 / 100 -> 33%
        assertThat(getJson("/api/goals/me/progress", u[1]).get("percentPrecise").asText())
                .isEqualTo("33.33");

        postJson("/api/splits/" + billId + "/reverse", u[0], body("reason", "wrong"));

        JsonNode progress = getJson("/api/goals/me/progress", u[1]);
        assertThat(progress.get("spent").asText()).isEqualTo("0.00");
        assertThat(progress.get("percentPrecise").asText()).isEqualTo("0.00");

        // payer's goal spending is unaffected - a receivable is not spending, and
        // neither is its reversal
        postJson("/api/goals/me", u[0], body("target", "100.00"));
        assertThat(getJson("/api/goals/me/progress", u[0]).get("spent").asText())
                .isEqualTo("0.00");
    }

    @Test
    void correctedSplitIsBookedAsNewBillNotAnUpdate() throws Exception {
        long[] u = crewOfThree();
        long wrongBill = finalizedBill(u);
        postJson("/api/splits/" + wrongBill + "/reverse", u[0], body("reason", "fix"));

        long correctBill = postJson("/api/splits", u[0],
                body("total", "90.00", "participantIds", java.util.List.of(u[1], u[2]),
                        "description", "corrected")).get("id").asLong();
        postJson("/api/splits/" + correctBill + "/confirm", u[1]);
        postJson("/api/splits/" + correctBill + "/confirm", u[2]);

        JsonNode u2 = getJson("/api/ledger/entries", u[1]);
        // wrong expense, its reversal, corrected expense
        assertThat(u2.findValuesAsText("amount")).containsExactly("33.33", "-33.33", "30.00");
        // history rows keep original split id
        assertThat(getJson("/api/splits/" + wrongBill, u[1]).get("status").asText())
                .isEqualTo("REVERSED");
    }
}

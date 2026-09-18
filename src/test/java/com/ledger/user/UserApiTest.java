package com.ledger.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledger.ApiTestBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserApiTest extends ApiTestBase {

    @Test
    void createsUsersAndAddsFriendBidirectionallyEnforcedByCaller() throws Exception {
        long alice = createUser("alice-u");
        long bob = createUser("bob-u");

        addFriend(alice, bob);
        JsonNode friends = getJson("/api/users/me/friends", alice);
        assertThat(friends).hasSize(1);
        assertThat(friends.get(0).get("id").asLong()).isEqualTo(bob);

        // adding again is idempotent
        addFriend(alice, bob);
        assertThat(getJson("/api/users/me/friends", alice)).hasSize(1);
    }

    @Test
    void rejectsDuplicateNamesAndSelfFriendshipAndUnknownUsers() throws Exception {
        createUser("carol-u");
        JsonNode dup = postJsonExpect("/api/users", null, body("name", "carol-u"), 409);
        assertThat(dup.get("error").asText()).isEqualTo("CONFLICT");

        long dave = createUser("dave-u");
        assertThat(postJsonExpect("/api/users/me/friends", dave, body("friendId", dave), 400)
                .get("message").asText()).contains("yourself");
        assertThat(postJsonExpect("/api/users/me/friends", dave, body("friendId", 99999L), 404)
                .get("error").asText()).isEqualTo("NOT_FOUND");
    }

    @Test
    void missingOrInvalidUserHeaderIsUnauthorized() throws Exception {
        assertThat(getExpect("/api/users/me/friends", null, 401).get("error").asText())
                .isEqualTo("UNAUTHORIZED");
        assertThat(postJsonExpect("/api/ledger/entries", -3L, body("amount", "1.00"), 401)
                .get("error").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(getExpect("/api/users/me/friends", 999999L, 404).get("error").asText())
                .isEqualTo("NOT_FOUND");
    }

    @Test
    void blankUserNameIsValidationError() throws Exception {
        assertThat(postJsonExpect("/api/users", null, body("name", "  "), 400)
                .get("error").asText()).isEqualTo("VALIDATION");
    }
}

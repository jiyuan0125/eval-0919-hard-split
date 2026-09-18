package com.ledger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.config.name=application-test")
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTestBase {

    protected static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-05-15T10:00:00Z"), ZoneId.of("UTC"));

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    protected long createUser(String name) throws Exception {
        return postJson("/api/users", null, Map.of("name", name)).get("id").asLong();
    }

    protected void addFriend(long owner, long friend) throws Exception {
        postJson("/api/users/me/friends", owner, Map.of("friendId", friend));
    }

    protected JsonNode postJson(String path, long userId) throws Exception {
        return postJson(path, Long.valueOf(userId), body());
    }

    protected JsonNode postJson(String path, Long userId, Object body) throws Exception {
        MvcResult result = mvc.perform(authed(post(path), userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andReturn();
        return parse(result);
    }

    protected JsonNode postJsonExpect(String path, long userId, int expected) throws Exception {
        return postJsonExpect(path, Long.valueOf(userId), body(), expected);
    }

    protected JsonNode postJsonExpect(String path, Long userId, Object body, int expected) throws Exception {
        MvcResult result = mvc.perform(authed(post(path), userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().is(expected))
                .andReturn();
        return parse(result);
    }

    protected JsonNode postRawExpect(String path, Long userId, String rawBody, int expected) throws Exception {
        MvcResult result = mvc.perform(authed(post(path), userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rawBody))
                .andExpect(status().is(expected))
                .andReturn();
        return parse(result);
    }

    protected JsonNode getJson(String path, long userId) throws Exception {
        MvcResult result = mvc.perform(authed(get(path), userId))
                .andExpect(status().isOk())
                .andReturn();
        return parse(result);
    }

    protected JsonNode getExpect(String path, Long userId, int expected) throws Exception {
        MvcResult result = mvc.perform(authed(get(path), userId))
                .andExpect(status().is(expected))
                .andReturn();
        return parse(result);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder authed(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, Long userId) {
        return request.header("X-User-Id", userId == null ? "" : userId.toString());
    }

    private JsonNode parse(MvcResult result) throws Exception {
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(body);
    }

    protected Map<String, Object> body(Object... kv) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }
}

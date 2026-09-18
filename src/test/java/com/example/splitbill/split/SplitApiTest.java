package com.example.splitbill.split;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.splitbill.user.AppUser;
import com.example.splitbill.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class SplitApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    UserRepository userRepository;

    Long payer;
    Long alice;
    Long bob;

    @BeforeEach
    void setUp() {
        payer = userRepository.save(new AppUser("payer")).getId();
        alice = userRepository.save(new AppUser("alice")).getId();
        bob = userRepository.save(new AppUser("bob")).getId();
    }

    private long createBill() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/splits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"payerId": %d, "title": "dinner", "totalCents": 10000,
                                 "friendIds": [%d, %d]}
                                """.formatted(payer, alice, bob)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payerShareCents").value(3333))
                .andExpect(jsonPath("$.participants.length()").value(2))
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get("id").asLong();
    }

    @Test
    void fullLifecycleOverHttp() throws Exception {
        long billId = createBill();

        mockMvc.perform(post("/api/splits/{id}/confirm", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d}".formatted(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"));

        mockMvc.perform(post("/api/splits/{id}/confirm", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d}".formatted(bob)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(get("/api/splits/{id}", billId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"));

        mockMvc.perform(post("/api/splits/{id}/reverse", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\": \"wrong amount\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void withdrawOverHttp() throws Exception {
        long billId = createBill();

        mockMvc.perform(post("/api/splits/{id}/withdraw", billId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d}".formatted(alice)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[?(@.userId == %d)].status".formatted(alice))
                        .value(org.hamcrest.Matchers.contains("WITHDRAWN")));
    }

    @Test
    void validationAndUnknownBillErrors() throws Exception {
        mockMvc.perform(post("/api/splits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\": %d, \"title\": \"x\", \"totalCents\": -5, \"friendIds\": []}"
                                .formatted(payer)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/splits/{id}", 987654))
                .andExpect(status().isNotFound());
    }
}

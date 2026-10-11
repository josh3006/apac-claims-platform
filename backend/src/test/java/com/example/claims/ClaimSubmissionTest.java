package com.example.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:submissions;DB_CLOSE_DELAY=-1", "app.events-enabled=false"})
@AutoConfigureMockMvc
class ClaimSubmissionTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClaimRepository claims;
    @Autowired OutboxRepository outbox;
    @Autowired JdbcTemplate jdbc;

    @Test
    void retryReplaysOriginalResponseWithoutAnotherClaimHistoryOrEvent() throws Exception {
        UUID key = UUID.randomUUID();
        long initialClaims = claims.count(), initialEvents = outbox.count();
        MvcResult first = submit(key, body("MY", 100), "claimant");
        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(first.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("false");
        JsonNode created = json.readTree(first.getResponse().getContentAsString());

        // Later lifecycle changes must not rewrite the original submission receipt.
        mvc.perform(post("/api/officer/claims/" + created.get("id").asText() + "/assign")
            .with(user("officer").roles("OFFICER")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("version", created.get("version").asLong(), "message", "Review"))))
            .andExpect(status().isOk());
        long eventsAfterAssignment = outbox.count();
        MvcResult retry = submit(key, body("MY", new java.math.BigDecimal("100.00")), "claimant");
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(retry.getResponse().getHeader("Idempotency-Replayed")).isEqualTo("true");
        assertThat(json.readTree(retry.getResponse().getContentAsString())).isEqualTo(created);
        assertThat(claims.count()).isEqualTo(initialClaims + 1);
        assertThat(eventsAfterAssignment).isEqualTo(initialEvents + 2);
        assertThat(outbox.count()).isEqualTo(eventsAfterAssignment);
    }

    @Test
    void sameKeyWithDifferentPayloadConflictsButAnotherClaimantHasAnIndependentKey() throws Exception {
        UUID key = UUID.randomUUID();
        var original = submit(key, body("MY", 100), "claimant");
        assertThat(original.getResponse().getStatus()).isEqualTo(201);
        var conflict = submit(key, body("MY", 101), "claimant");
        assertThat(conflict.getResponse().getStatus()).isEqualTo(409);
        var independent = submit(key, body("MY", 101), "other-claimant");
        assertThat(independent.getResponse().getStatus()).isEqualTo(201);
        assertThat(json.readTree(independent.getResponse().getContentAsString()).get("id"))
            .isNotEqualTo(json.readTree(original.getResponse().getContentAsString()).get("id"));
    }

    @Test
    void domainFailureRollsBackReservationAndAllowsCorrectedSubmission() throws Exception {
        UUID key = UUID.randomUUID();
        long initialClaims = claims.count(), initialEvents = outbox.count();
        assertThat(submit(key, body("XX", 100), "claimant").getResponse().getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_submissions WHERE submission_key = ?", Long.class, key)).isZero();
        assertThat(claims.count()).isEqualTo(initialClaims);
        assertThat(outbox.count()).isEqualTo(initialEvents);
        assertThat(submit(key, body("MY", 100), "claimant").getResponse().getStatus()).isEqualTo(201);
    }

    @Test
    void missingOrMalformedKeyIsRejected() throws Exception {
        for (String key : new String[]{null, "not-a-uuid", ""}) {
            var request = post("/api/claims").with(user("claimant").roles("CLAIMANT"))
                .contentType("application/json").content(body("MY", 100));
            if (key != null) request.header("Idempotency-Key", key);
            mvc.perform(request).andExpect(status().isBadRequest());
        }
    }

    @Test
    void simultaneousRetriesCreateExactlyOneClaimAndEvent() throws Exception {
        UUID key = UUID.randomUUID();
        long initialClaims = claims.count(), initialEvents = outbox.count();
        var ready = new CountDownLatch(4);
        var start = new CountDownLatch(1);
        String payload = body("MY", 100);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var futures = new ArrayList<java.util.concurrent.Future<MvcResult>>();
            for (int i = 0; i < 4; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Test start timed out");
                    return submit(key, payload, "claimant");
                }));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var statuses = new ArrayList<Integer>();
            var ids = new java.util.HashSet<String>();
            for (var future : futures) {
                var result = future.get(20, TimeUnit.SECONDS);
                statuses.add(result.getResponse().getStatus());
                ids.add(json.readTree(result.getResponse().getContentAsString()).get("id").asText());
            }
            assertThat(statuses).containsExactlyInAnyOrder(201, 200, 200, 200);
            assertThat(ids).hasSize(1);
            assertThat(claims.count()).isEqualTo(initialClaims + 1);
            assertThat(outbox.count()).isEqualTo(initialEvents + 1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM claim_submissions WHERE submission_key = ?", Long.class, key)).isEqualTo(1);
        }
    }

    private MvcResult submit(UUID key, String body, String claimant) throws Exception {
        return mvc.perform(post("/api/claims").with(user(claimant).roles("CLAIMANT"))
            .header("Idempotency-Key", key).contentType("application/json").content(body)).andReturn();
    }

    private String body(String market, Number amount) throws Exception {
        return json.writeValueAsString(Map.of("type", "MOTOR", "market", market, "incidentDate", "2026-01-01",
            "description", "Bumper damage", "estimatedAmount", amount));
    }
}

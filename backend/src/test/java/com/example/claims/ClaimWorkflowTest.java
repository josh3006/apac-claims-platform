package com.example.claims;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;
import java.math.BigDecimal;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:workflow;DB_CLOSE_DELAY=-1", "app.events-enabled=false"})
@AutoConfigureMockMvc
class ClaimWorkflowTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired ClaimRepository claims;
    @Autowired OutboxRepository outbox;
    @Autowired ClaimService service;

    @Test
    @WithMockUser(username = "claimant", roles = "CLAIMANT")
    void serviceRejectsOfficerOperationsWithoutTheHttpController() {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        var id = java.util.UUID.randomUUID();
        var change = new ApiModels.Change(0L, "Review");
        // Direct bean calls demonstrate that endpoint filters are not the only role boundary.
        assertThatThrownBy(() -> service.assign(id, change, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.requestInformation(id, change, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.decide(id, new ApiModels.Decision(0L, Claim.Status.REJECTED, "Reason", null), actor))
            .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.dashboard()).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "officer", roles = "OFFICER")
    void serviceRejectsClaimantOperationsWithoutTheHttpController() {
        var actor = SecurityContextHolder.getContext().getAuthentication();
        var request = new ApiModels.Submit(Claim.Type.MOTOR, "MY", java.time.LocalDate.of(2026, 1, 1), "Damage", BigDecimal.TEN);
        assertThatThrownBy(() -> service.submit(request, actor)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> service.provideInformation(java.util.UUID.randomUUID(), new ApiModels.Change(0L, "Details"), actor))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void fullInformationAndSettlementFlowHasHistoryAndAtomicEvents() throws Exception {
        JsonNode claim = submit("SG", "100.00");
        String id = claim.get("id").asText();
        long initialPending = outbox.countByPublishedAtIsNull();
        mvc.perform(get("/api/claims/" + id).with(user("other").roles("CLAIMANT"))).andExpect(status().isNotFound());
        mvc.perform(post("/api/officer/claims/" + id + "/assign").with(user("claimant").roles("CLAIMANT"))
            .contentType("application/json").content("{\"version\":0,\"message\":\"Review\"}")).andExpect(status().isForbidden());
        claim = change(id, "assign", claim, "Review started");
        assertThat(claim.get("status").asText()).isEqualTo("UNDER_REVIEW");
        // Stale update is rejected without a second event.
        mvc.perform(post("/api/officer/claims/" + id + "/request-information").with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", 0, "message", "Stale"))))
            .andExpect(status().isConflict());
        mvc.perform(post("/api/officer/claims/" + id + "/request-information").with(user("other-officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", claim.get("version").asLong(), "message", "Question"))))
            .andExpect(status().isForbidden());
        claim = change(id, "request-information", claim, "Please explain the damage");
        claim = perform(post("/api/claims/" + id + "/information").with(user("claimant").roles("CLAIMANT"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", claim.get("version").asLong(), "message", "Bumper damaged"))), 200);
        claim = perform(post("/api/officer/claims/" + id + "/decision").with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", claim.get("version").asLong(), "status", "SETTLED", "message", "Approved", "settlementAmount", 80))), 200);
        assertThat(claim.get("status").asText()).isEqualTo("SETTLED");
        assertThat(claim.get("history").size()).isEqualTo(5);
        assertThat(outbox.countByPublishedAtIsNull()).isEqualTo(initialPending + 4);
        changeExpectConflict(id, "assign", claim);
    }

    @Test
    void rejectsInvalidInputsAndUnauthenticatedRequests() throws Exception {
        mvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me").with(httpBasic("claimant", "claimant-demo"))).andExpect(status().isOk()).andExpect(jsonPath("role").value("CLAIMANT"));
        mvc.perform(get("/api/me").with(httpBasic("claimant", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/officer/dashboard").with(user("claimant").roles("CLAIMANT"))).andExpect(status().isForbidden());
        for (String amount : new String[]{"-1", "0", "1.123", "1000000000"}) {
            mvc.perform(post("/api/claims").with(user("claimant").roles("CLAIMANT"))
                .header("Idempotency-Key", java.util.UUID.randomUUID())
                .contentType("application/json").content(submitBody("MY", amount))).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/claims").with(user("claimant").roles("CLAIMANT"))
            .header("Idempotency-Key", java.util.UUID.randomUUID())
            .contentType("application/json").content(submitBody("XX", "10"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/claims/not-a-uuid").with(user("claimant").roles("CLAIMANT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/claims").with(user("claimant").roles("CLAIMANT"))
            .header("Idempotency-Key", java.util.UUID.randomUUID())
            .contentType("application/json").content(submitBody("MY", "10").replace("2026-01-01", "2099-01-01"))).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsPrematureDecisionsAndReportsExposurePerCurrency() throws Exception {
        JsonNode my = submit("MY", "120");
        JsonNode sg = submit("SG", "240");
        String id = my.get("id").asText();
        mvc.perform(post("/api/claims/" + id + "/information").with(user("claimant").roles("CLAIMANT"))
            .contentType("application/json").content("{\"version\":0,\"message\":\"Unrequested\"}")).andExpect(status().isConflict());
        JsonNode dashboard = perform(get("/api/officer/dashboard").with(user("officer").roles("OFFICER")), 200);
        assertThat(dashboard.get("outstandingExposureByCurrency").get("MYR").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("120"));
        assertThat(dashboard.get("outstandingExposureByCurrency").get("SGD").decimalValue()).isGreaterThanOrEqualTo(new BigDecimal("240"));
        my = change(id, "assign", my, "Review");
        mvc.perform(post("/api/officer/claims/" + id + "/decision").with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", my.get("version").asLong(), "status", "SETTLED", "message", "Missing amount"))))
            .andExpect(status().isBadRequest());
        JsonNode rejected = perform(post("/api/officer/claims/" + id + "/decision").with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", my.get("version").asLong(), "status", "REJECTED", "message", "Outside demo coverage"))), 200);
        assertThat(rejected.get("settlementAmount").isNull()).isTrue();
        changeExpectConflict(id, "request-information", rejected);
        // Claimants see their own claims only.
        JsonNode others = perform(get("/api/claims").with(user("different").roles("CLAIMANT")), 200);
        assertThat(others.size()).isZero();
    }

    private JsonNode submit(String market, String amount) throws Exception {
        return perform(post("/api/claims").with(user("claimant").roles("CLAIMANT"))
            .header("Idempotency-Key", java.util.UUID.randomUUID())
            .contentType("application/json").content(submitBody(market, amount)), 201);
    }
    private String submitBody(String market, String amount) throws Exception {
        return json.writeValueAsString(Map.of("type", "MOTOR", "market", market, "incidentDate", "2026-01-01", "description", "Vehicle damage", "estimatedAmount", new BigDecimal(amount)));
    }
    private JsonNode change(String id, String action, JsonNode claim, String message) throws Exception {
        return perform(post("/api/officer/claims/" + id + "/" + action).with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", claim.get("version").asLong(), "message", message))), 200);
    }
    private void changeExpectConflict(String id, String action, JsonNode claim) throws Exception {
        mvc.perform(post("/api/officer/claims/" + id + "/" + action).with(user("officer").roles("OFFICER"))
            .contentType("application/json").content(json.writeValueAsString(Map.of("version", claim.get("version").asLong(), "message", "Invalid transition"))))
            .andExpect(status().isConflict());
    }
    private JsonNode perform(MockHttpServletRequestBuilder request, int expectedStatus) throws Exception {
        return json.readTree(mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString());
    }
}

package com.example.claims;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import static com.example.claims.ApiModels.*;

/**
 * Transaction boundary for a submission receipt, claim, history, and outbox event.
 * JDBC and JPA use the same datasource/transaction; an incomplete receipt is never committed.
 */
@Service
class SubmissionTransaction {
    private final JdbcTemplate jdbc;
    private final ClaimService claims;
    private final ObjectMapper json;

    SubmissionTransaction(JdbcTemplate jdbc, ClaimService claims, ObjectMapper json) {
        this.jdbc = jdbc;
        this.claims = claims;
        this.json = json;
    }

    String fingerprint(Submit request) {
        // Equivalent decimal notation (100 and 100.00) represents the same request.
        Submit canonical = new Submit(request.type(), request.market(), request.incidentDate(),
            request.description(), request.estimatedAmount().setScale(2));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(canonical)));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("Cannot fingerprint submission", e);
        }
    }

    @Transactional
    public ClaimView create(UUID key, String fingerprint, Submit request, Authentication actor) {
        jdbc.update("INSERT INTO claim_submissions (claimant, submission_key, request_hash) VALUES (?, ?, ?)",
            actor.getName(), key, fingerprint);
        ClaimView claim = claims.submit(request, actor);
        try {
            jdbc.update("UPDATE claim_submissions SET claim_id = ?, response_json = ? WHERE claimant = ? AND submission_key = ?",
                claim.id(), json.writeValueAsString(claim), actor.getName(), key);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot record submission response", e);
        }
        return claim;
    }

    @Transactional(readOnly = true)
    public ClaimView replay(UUID key, String fingerprint, String claimant) {
        return jdbc.queryForObject("SELECT request_hash, response_json FROM claim_submissions WHERE claimant = ? AND submission_key = ?",
            (row, index) -> {
                if (!fingerprint.equals(row.getString("request_hash"))) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different submission");
                }
                try {
                    // Replay the original result even if the claim has since progressed; GET returns current state.
                    return json.readValue(row.getString("response_json"), ClaimView.class);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("Cannot read submission response", e);
                }
            }, claimant, key);
    }
}

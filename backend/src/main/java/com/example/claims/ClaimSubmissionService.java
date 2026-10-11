package com.example.claims;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import java.util.UUID;
import static com.example.claims.ApiModels.*;

/**
 * Coordinates idempotent submission. A competing insert must finish rolling back before replay
 * is read in a new transaction; PostgreSQL does not allow reads inside a failed transaction.
 */
@Service
@PreAuthorize("hasRole('CLAIMANT')")
public class ClaimSubmissionService {
    private final SubmissionTransaction transaction;

    ClaimSubmissionService(SubmissionTransaction transaction) {
        this.transaction = transaction;
    }

    public SubmissionResult submit(UUID key, Submit request, Authentication actor) {
        String fingerprint = transaction.fingerprint(request);
        try {
            return new SubmissionResult(transaction.create(key, fingerprint, request, actor), false);
        } catch (DuplicateKeyException duplicate) {
            // The unique constraint serialises same-key competitors across application instances.
            return new SubmissionResult(transaction.replay(key, fingerprint, actor.getName()), true);
        }
    }

    record SubmissionResult(ClaimView claim, boolean replayed) {}
}

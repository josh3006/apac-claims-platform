package com.example.claims;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import static com.example.claims.ApiModels.*;
import static com.example.claims.Claim.Status.*;

/**
 * Owns claim lifecycle rules, access checks, and atomic persistence of claim changes and events.
 *
 * <p>Every mutation runs in a database transaction. Kafka publication happens separately, so a
 * broker outage cannot undo an accepted claim change. Method security enforces roles even when
 * another Spring bean calls this service without going through the HTTP controller.</p>
 */
@Service
@Transactional
@PreAuthorize("hasAnyRole('CLAIMANT', 'OFFICER')")
public class ClaimService {
    // These six markets are demo assumptions, not a statement of Chubb's actual footprint.
    static final Map<String, String> CURRENCIES = Map.of("MY", "MYR", "SG", "SGD", "HK", "HKD",
        "TH", "THB", "ID", "IDR", "VN", "VND");
    private final ClaimRepository claims;
    private final OutboxRepository outbox;
    ClaimService(ClaimRepository claims, OutboxRepository outbox) {
        this.claims = claims;
        this.outbox = outbox;
    }

    static boolean officer(Authentication user) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
    }

    /** Creates a claim owned by the authenticated claimant; the client cannot choose its owner. */
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimView submit(Submit request, Authentication user) {
        if (officer(user)) throw error(HttpStatus.FORBIDDEN, "Only claimants can submit claims");
        if (!CURRENCIES.containsKey(request.market())) throw error(HttpStatus.BAD_REQUEST, "Unsupported market");
        Claim claim = new Claim();
        claim.claimant = user.getName();
        claim.type = request.type();
        claim.market = request.market();
        claim.currency = CURRENCIES.get(claim.market);
        claim.incidentDate = request.incidentDate();
        claim.description = request.description();
        claim.estimatedAmount = request.estimatedAmount();
        claim.record(user.getName(), "Claim submitted");
        return save(claim);
    }

    @Transactional(readOnly = true)
    public List<ClaimView> list(Authentication user) {
        return (officer(user) ? claims.findAllByOrderByCreatedAtDesc()
            : claims.findByClaimantOrderByCreatedAtDesc(user.getName())).stream().map(ClaimView::from).toList();
    }

    @Transactional(readOnly = true)
    public ClaimView get(UUID id, Authentication user) {
        return ClaimView.from(visible(id, user));
    }

    /** Self-assigns a submitted claim; reassignment and reopening are outside this workflow. */
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimView assign(UUID id, Change request, Authentication user) {
        Claim claim = visible(id, user);
        checkVersion(claim, request.version());
        require(claim.status == SUBMITTED, "Only submitted claims can be picked up");
        claim.assignedOfficer = user.getName();
        claim.status = UNDER_REVIEW;
        claim.record(user.getName(), request.message());
        return save(claim);
    }

    /** Pauses assessment until the owning claimant supplies the requested information. */
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimView requestInformation(UUID id, Change request, Authentication user) {
        Claim claim = visible(id, user);
        checkVersion(claim, request.version());
        assigned(claim, user);
        require(claim.status == UNDER_REVIEW, "Claim must be under review");
        claim.status = INFORMATION_REQUIRED;
        claim.record(user.getName(), request.message());
        return save(claim);
    }

    /** Records claimant-visible correspondence and returns the claim to its assigned officer. */
    @PreAuthorize("hasRole('CLAIMANT')")
    public ClaimView provideInformation(UUID id, Change request, Authentication user) {
        Claim claim = visible(id, user);
        checkVersion(claim, request.version());
        if (officer(user)) throw error(HttpStatus.FORBIDDEN, "Only the claimant can provide information");
        require(claim.status == INFORMATION_REQUIRED, "Information has not been requested");
        claim.status = UNDER_REVIEW;
        claim.record(user.getName(), request.message());
        return save(claim);
    }

    /** Records a final assessment decision. SETTLED records approval, not an executed payment. */
    @PreAuthorize("hasRole('OFFICER')")
    public ClaimView decide(UUID id, Decision request, Authentication user) {
        Claim claim = visible(id, user);
        checkVersion(claim, request.version());
        assigned(claim, user);
        require(claim.status == UNDER_REVIEW, "Claim must be under review before a decision");
        if (request.status() != SETTLED && request.status() != REJECTED)
            throw error(HttpStatus.BAD_REQUEST, "Decision must be SETTLED or REJECTED");
        if (request.status() == SETTLED && request.settlementAmount() == null)
            throw error(HttpStatus.BAD_REQUEST, "Settlement amount is required");
        if (request.status() == REJECTED && request.settlementAmount() != null)
            throw error(HttpStatus.BAD_REQUEST, "Rejected claims cannot have a settlement amount");
        claim.status = request.status();
        claim.settlementAmount = request.settlementAmount();
        claim.record(user.getName(), request.message());
        return save(claim);
    }

    /**
     * Summarises the demo queue. Exposure is a submitted estimate, not a verified reserve.
     * Amounts stay separated by currency because the application has no FX conversion policy.
     */
    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('OFFICER')")
    public Dashboard dashboard() {
        Map<String, Long> statuses = new TreeMap<>();
        Map<String, Long> workload = new TreeMap<>();
        Map<String, BigDecimal> exposure = new TreeMap<>();
        Map<String, BigDecimal> settlements = new TreeMap<>();
        for (Claim.Status status : Claim.Status.values()) statuses.put(status.name(), 0L);
        for (Claim c : claims.findAll()) {
            statuses.merge(c.status.name(), 1L, Long::sum);
            if (c.status != SETTLED && c.status != REJECTED) {
                workload.merge(c.assignedOfficer == null ? "Unassigned" : c.assignedOfficer, 1L, Long::sum);
                exposure.merge(c.currency, c.estimatedAmount, BigDecimal::add);
            } else if (c.status == SETTLED) settlements.merge(c.currency, c.settlementAmount, BigDecimal::add);
        }
        return new Dashboard(statuses, workload, exposure, settlements);
    }

    private Claim visible(UUID id, Authentication user) {
        Claim c = claims.findById(id).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Claim not found"));
        // Hide existence of another claimant's claim.
        if (!officer(user) && !c.claimant.equals(user.getName())) throw error(HttpStatus.NOT_FOUND, "Claim not found");
        return c;
    }
    private void checkVersion(Claim c, long expected) {
        // Reject an already stale request here; JPA @Version also catches races at commit time.
        require(c.version == expected, "Claim changed; refresh and try again");
    }
    private void assigned(Claim c, Authentication user) {
        if (!user.getName().equals(c.assignedOfficer)) throw error(HttpStatus.FORBIDDEN, "Only the assigned officer may assess this claim");
    }
    private ClaimView save(Claim c) {
        // Flush obtains the managed instance and updated version; it does not commit the transaction.
        c = claims.saveAndFlush(c);
        // A later failure rolls back both writes. Sending directly to Kafka here would lose atomicity.
        outbox.save(new OutboxEvent(c));
        return ClaimView.from(c);
    }
    private void require(boolean condition, String message) { if (!condition) throw error(HttpStatus.CONFLICT, message); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}

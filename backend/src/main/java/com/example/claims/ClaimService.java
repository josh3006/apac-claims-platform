package com.example.claims;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import static com.example.claims.ApiModels.*;
import static com.example.claims.Claim.Status.*;

@Service
@Transactional
class ClaimService {
    // These six markets are demo assumptions, not a statement of Chubb's actual footprint.
    static final Map<String, String> CURRENCIES = Map.of("MY", "MYR", "SG", "SGD", "HK", "HKD",
        "TH", "THB", "ID", "IDR", "VN", "VND");
    private final ClaimRepository claims;
    private final OutboxRepository outbox;
    ClaimService(ClaimRepository claims, OutboxRepository outbox) { this.claims = claims; this.outbox = outbox; }

    static boolean officer(Authentication user) {
        return user.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_OFFICER"));
    }

    ClaimView submit(Submit request, Authentication user) {
        if (officer(user)) throw error(HttpStatus.FORBIDDEN, "Only claimants can submit claims");
        if (!CURRENCIES.containsKey(request.market())) throw error(HttpStatus.BAD_REQUEST, "Unsupported market");
        Claim c = new Claim();
        c.claimant = user.getName(); c.type = request.type(); c.market = request.market();
        c.currency = CURRENCIES.get(c.market); c.incidentDate = request.incidentDate();
        c.description = request.description(); c.estimatedAmount = request.estimatedAmount();
        c.record(user.getName(), "Claim submitted");
        return save(c);
    }

    @Transactional(readOnly = true)
    List<ClaimView> list(Authentication user) {
        return (officer(user) ? claims.findAllByOrderByCreatedAtDesc()
            : claims.findByClaimantOrderByCreatedAtDesc(user.getName())).stream().map(ClaimView::from).toList();
    }

    @Transactional(readOnly = true)
    ClaimView get(UUID id, Authentication user) { return ClaimView.from(visible(id, user)); }

    ClaimView assign(UUID id, Change request, Authentication user) {
        Claim c = visible(id, user); checkVersion(c, request.version());
        require(c.status == SUBMITTED, "Only submitted claims can be picked up");
        c.assignedOfficer = user.getName(); c.status = UNDER_REVIEW;
        c.record(user.getName(), request.message()); return save(c);
    }

    ClaimView requestInformation(UUID id, Change request, Authentication user) {
        Claim c = visible(id, user); checkVersion(c, request.version()); assigned(c, user);
        require(c.status == UNDER_REVIEW, "Claim must be under review");
        c.status = INFORMATION_REQUIRED; c.record(user.getName(), request.message()); return save(c);
    }

    ClaimView provideInformation(UUID id, Change request, Authentication user) {
        Claim c = visible(id, user); checkVersion(c, request.version());
        if (officer(user)) throw error(HttpStatus.FORBIDDEN, "Only the claimant can provide information");
        require(c.status == INFORMATION_REQUIRED, "Information has not been requested");
        c.status = UNDER_REVIEW; c.record(user.getName(), request.message()); return save(c);
    }

    ClaimView decide(UUID id, Decision request, Authentication user) {
        Claim c = visible(id, user); checkVersion(c, request.version()); assigned(c, user);
        require(c.status == UNDER_REVIEW, "Claim must be under review before a decision");
        if (request.status() != SETTLED && request.status() != REJECTED)
            throw error(HttpStatus.BAD_REQUEST, "Decision must be SETTLED or REJECTED");
        if (request.status() == SETTLED && request.settlementAmount() == null)
            throw error(HttpStatus.BAD_REQUEST, "Settlement amount is required");
        if (request.status() == REJECTED && request.settlementAmount() != null)
            throw error(HttpStatus.BAD_REQUEST, "Rejected claims cannot have a settlement amount");
        c.status = request.status(); c.settlementAmount = request.settlementAmount();
        c.record(user.getName(), request.message()); return save(c);
    }

    @Transactional(readOnly = true)
    Dashboard dashboard() {
        Map<String, Long> statuses = new TreeMap<>(), workload = new TreeMap<>();
        Map<String, BigDecimal> exposure = new TreeMap<>(), settlements = new TreeMap<>();
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
    private void checkVersion(Claim c, long expected) { require(c.version == expected, "Claim changed; refresh and try again"); }
    private void assigned(Claim c, Authentication user) {
        if (!user.getName().equals(c.assignedOfficer)) throw error(HttpStatus.FORBIDDEN, "Only the assigned officer may assess this claim");
    }
    private ClaimView save(Claim c) {
        claims.saveAndFlush(c); // Flush increments @Version before returning the response.
        outbox.save(new OutboxEvent(c)); // Same DB transaction: either claim + event both persist, or neither does.
        return ClaimView.from(c);
    }
    private void require(boolean condition, String message) { if (!condition) throw error(HttpStatus.CONFLICT, message); }
    private ResponseStatusException error(HttpStatus status, String message) { return new ResponseStatusException(status, message); }
}

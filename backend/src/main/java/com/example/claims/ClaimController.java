package com.example.claims;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static com.example.claims.ApiModels.*;

/**
 * Maps HTTP requests to claim operations without implementing lifecycle rules.
 * Bean Validation checks request shapes; Spring Security supplies the authenticated principal.
 */
@RestController
@RequestMapping("/api")
class ClaimController {
    private final ClaimService service;
    private final ClaimSubmissionService submissions;
    ClaimController(ClaimService service, ClaimSubmissionService submissions) {
        this.service = service;
        this.submissions = submissions;
    }
    @GetMapping("/me") Map<String, String> me(Authentication user) {
        return Map.of("username", user.getName(), "role", ClaimService.officer(user) ? "OFFICER" : "CLAIMANT");
    }
    @PostMapping("/claims")
    ResponseEntity<ClaimView> submit(@RequestHeader("Idempotency-Key") UUID key,
                                   @Valid @RequestBody Submit request, Authentication user) {
        var result = submissions.submit(key, request, user);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
            .header("Idempotency-Replayed", Boolean.toString(result.replayed())).body(result.claim());
    }
    @GetMapping("/claims") List<ClaimView> list(Authentication user) { return service.list(user); }
    @GetMapping("/claims/{id}") ClaimView get(@PathVariable UUID id, Authentication user) { return service.get(id, user); }
    @PostMapping("/claims/{id}/information")
    ClaimView information(@PathVariable UUID id, @Valid @RequestBody Change request, Authentication user) {
        return service.provideInformation(id, request, user);
    }
    @PostMapping("/officer/claims/{id}/assign")
    ClaimView assign(@PathVariable UUID id, @Valid @RequestBody Change request, Authentication user) {
        return service.assign(id, request, user);
    }
    @PostMapping("/officer/claims/{id}/request-information")
    ClaimView requestInformation(@PathVariable UUID id, @Valid @RequestBody Change request, Authentication user) {
        return service.requestInformation(id, request, user);
    }
    @PostMapping("/officer/claims/{id}/decision")
    ClaimView decision(@PathVariable UUID id, @Valid @RequestBody Decision request, Authentication user) {
        return service.decide(id, request, user);
    }
    @GetMapping("/officer/dashboard") Dashboard dashboard() { return service.dashboard(); }
}

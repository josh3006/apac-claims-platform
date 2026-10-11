package com.example.claims;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable API contracts that separate persistence entities from JSON responses.
 * Input annotations validate shape and bounds; ClaimService validates state-dependent business rules.
 */
class ApiModels {
    record Submit(@NotNull Claim.Type type, @NotBlank String market,
                  @NotNull @PastOrPresent LocalDate incidentDate,
                  @NotBlank @Size(max = 2000) String description,
                  @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal estimatedAmount) {}
    /** The version is the client's last observed value and is required to reject stale changes. */
    record Change(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String message) {}
    // Settlement amount is conditional: required for SETTLED and forbidden for REJECTED in the service.
    record Decision(@NotNull @PositiveOrZero Long version, @NotNull Claim.Status status,
                    @NotBlank @Size(max = 2000) String message,
                    @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal settlementAmount) {}
    record HistoryView(Instant occurredAt, String actor, String status, String message) {}
    record ClaimView(UUID id, long version, String claimant, Claim.Type type, String market,
                     String currency, LocalDate incidentDate, String description, BigDecimal estimatedAmount,
                     BigDecimal settlementAmount, Claim.Status status, String assignedOfficer,
                     Instant createdAt, Instant updatedAt, List<HistoryView> history) {
        static ClaimView from(Claim c) {
            return new ClaimView(c.id, c.version, c.claimant, c.type, c.market, c.currency,
                c.incidentDate, c.description, c.estimatedAmount, c.settlementAmount, c.status,
                c.assignedOfficer, c.createdAt, c.updatedAt, c.history.stream()
                    .map(h -> new HistoryView(h.occurredAt, h.actor, h.status, h.message)).toList());
        }
    }
    /** Currency keys are preserved; totals must not combine monetary values across currencies. */
    record Dashboard(Map<String, Long> byStatus, Map<String, Long> workloadByOfficer,
                     Map<String, BigDecimal> outstandingExposureByCurrency,
                     Map<String, BigDecimal> settledAmountByCurrency) {}
}

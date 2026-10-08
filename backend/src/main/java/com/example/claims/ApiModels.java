package com.example.claims;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Java records are immutable request/response DTOs; entities never leave the service layer.
class ApiModels {
    record Submit(@NotNull Claim.Type type, @NotBlank String market,
                  @NotNull @PastOrPresent LocalDate incidentDate,
                  @NotBlank @Size(max = 2000) String description,
                  @NotNull @DecimalMin("0.01") @DecimalMax("999999999.99") @Digits(integer = 9, fraction = 2) BigDecimal estimatedAmount) {}
    record Change(@NotNull @PositiveOrZero Long version, @NotBlank @Size(max = 2000) String message) {}
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
    record Dashboard(Map<String, Long> byStatus, Map<String, Long> workloadByOfficer,
                     Map<String, BigDecimal> outstandingExposureByCurrency,
                     Map<String, BigDecimal> settledAmountByCurrency) {}
}

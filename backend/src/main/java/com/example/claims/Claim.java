package com.example.claims;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "claims")
class Claim {
    enum Status { SUBMITTED, UNDER_REVIEW, INFORMATION_REQUIRED, SETTLED, REJECTED }
    enum Type { MOTOR, PROPERTY }
    @Id UUID id = UUID.randomUUID();
    @Version long version;
    @Column(nullable = false) String claimant;
    @Enumerated(EnumType.STRING) Type type;
    String market;
    String currency;
    LocalDate incidentDate;
    @Column(length = 2000) String description;
    @Column(precision = 19, scale = 2) BigDecimal estimatedAmount;
    @Column(precision = 19, scale = 2) BigDecimal settlementAmount;
    @Enumerated(EnumType.STRING) Status status = Status.SUBMITTED;
    String assignedOfficer;
    Instant createdAt = Instant.now();
    Instant updatedAt = createdAt;
    @ElementCollection
    @CollectionTable(name = "claim_history", joinColumns = @JoinColumn(name = "claim_id"))
    @OrderColumn(name = "sequence_number")
    List<History> history = new ArrayList<>();

    protected Claim() {}

    void record(String actor, String message) {
        updatedAt = Instant.now();
        history.add(new History(updatedAt, actor, status.name(), message));
    }

    @Embeddable
    static class History {
        Instant occurredAt;
        String actor;
        String status;
        @Column(length = 2000) String message;
        protected History() {}
        History(Instant occurredAt, String actor, String status, String message) {
            this.occurredAt = occurredAt; this.actor = actor; this.status = status; this.message = message;
        }
    }
}

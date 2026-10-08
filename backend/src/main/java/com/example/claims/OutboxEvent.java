package com.example.claims;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id UUID id = UUID.randomUUID();
    UUID claimId;
    long claimVersion;
    String status;
    Instant occurredAt = Instant.now();
    Instant publishedAt;
    protected OutboxEvent() {}
    OutboxEvent(Claim c) { claimId = c.id; claimVersion = c.version; status = c.status.name(); }
}

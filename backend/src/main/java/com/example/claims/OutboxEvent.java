package com.example.claims;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * Durable notification intent saved in the same transaction as a claim mutation.
 * Publication is asynchronous; the event UUID is the consumer's deduplication key.
 */
@Entity
@Table(name = "outbox_events")
class OutboxEvent {
    @Id UUID id = UUID.randomUUID();
    UUID claimId;
    long claimVersion;
    String status;
    Instant occurredAt = Instant.now();
    // Null means publication is still pending, including after a broker failure.
    Instant publishedAt;
    protected OutboxEvent() {}
    OutboxEvent(Claim c) { claimId = c.id; claimVersion = c.version; status = c.status.name(); }
}

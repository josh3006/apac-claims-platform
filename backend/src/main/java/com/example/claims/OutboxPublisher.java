package com.example.claims;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Publishes durable claim-change events without blocking claim transactions.
 *
 * <p>Runs as a single instance in this demo. Delivery is at least once: a crash after Kafka
 * acknowledgement but before the publication marker is saved can replay an event. Consumers
 * must deduplicate by eventId. Multiple publisher instances would need row leasing or locking.</p>
 */
@Component
@ConditionalOnProperty(name = "app.events-enabled", havingValue = "true")
class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final OutboxRepository events;
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper json;
    OutboxPublisher(OutboxRepository events, KafkaTemplate<String, String> kafka, ObjectMapper json) {
        this.events = events; this.kafka = kafka; this.json = json;
    }
    @Scheduled(fixedDelay = 5000)
    void publish() {
        for (OutboxEvent event : events.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()) {
            try {
                String payload = json.writeValueAsString(Map.of("eventId", event.id, "eventType", "CLAIM_CHANGED",
                    "schemaVersion", 1, "claimId", event.claimId, "claimVersion", event.claimVersion,
                    "status", event.status, "occurredAt", event.occurredAt));
                // Event payloads exclude claimant descriptions and correspondence to limit data exposure.
                // Wait for acknowledgement before marking published; Kafka's key groups one claim's events.
                kafka.send("claim-events", event.claimId.toString(), payload).get(12, TimeUnit.SECONDS);
                event.publishedAt = Instant.now(); events.save(event);
            } catch (InterruptedException e) {
                // Preserve cancellation so shutdown can stop the publisher promptly.
                Thread.currentThread().interrupt(); return;
            } catch (Exception e) {
                log.warn("Event {} remains pending: {}", event.id, e.getClass().getSimpleName());
                return; // Retry on next scheduled run; a broker outage never rolls back the claim.
            }
        }
    }
}

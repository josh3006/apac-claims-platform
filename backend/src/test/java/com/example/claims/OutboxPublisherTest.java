package com.example.claims;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxPublisherTest {
    @Test
    void acknowledgementMarksPublishedAndPayloadExcludesPersonalText() throws Exception {
        OutboxRepository repository = mock(OutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        Claim claim = new Claim(); claim.version = 2L; claim.description = "private details";
        OutboxEvent event = new OutboxEvent(claim);
        when(repository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(kafka.send(eq("claim-events"), eq(claim.id.toString()), anyString()))
            .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());
        new OutboxPublisher(repository, kafka, json).publish();
        assertThat(event.publishedAt).isNotNull();
        verify(repository).save(event);
        var payload = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafka).send(eq("claim-events"), eq(claim.id.toString()), payload.capture());
        var body = json.readTree(payload.getValue());
        assertThat(body.get("eventId").asText()).isEqualTo(event.id.toString());
        assertThat(body.get("claimVersion").asLong()).isEqualTo(2);
        assertThat(payload.getValue()).doesNotContain("private details", "description", "claimant");
    }

    @Test
    void brokerFailureLeavesEventPendingForRetry() {
        OutboxRepository repository = mock(OutboxRepository.class);
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        Claim claim = new Claim(); claim.version = 0L;
        OutboxEvent event = new OutboxEvent(claim);
        when(repository.findTop50ByPublishedAtIsNullOrderByOccurredAtAsc()).thenReturn(List.of(event));
        when(kafka.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
        new OutboxPublisher(repository, kafka, new ObjectMapper().registerModule(new JavaTimeModule())).publish();
        assertThat(event.publishedAt).isNull();
        verify(repository, never()).save(any());
    }
}

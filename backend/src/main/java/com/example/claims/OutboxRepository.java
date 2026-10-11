package com.example.claims;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

/** Queries publication state; the bounded batch prevents one scheduler run from loading the whole backlog. */
interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findTop50ByPublishedAtIsNullOrderByOccurredAtAsc();
    long countByPublishedAtIsNull();
}

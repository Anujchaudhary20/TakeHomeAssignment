package com.ledger.webhook.repository;

import com.ledger.webhook.model.WebhookInboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WebhookInboxRepository extends JpaRepository<WebhookInboxEvent, UUID> {

    Optional<WebhookInboxEvent> findByEventId(String eventId);
}

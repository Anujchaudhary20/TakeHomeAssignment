-- Migration V3: Webhook Inbox table for payment provider event deduplication and recovery
CREATE TABLE webhook_inbox (
    id UUID PRIMARY KEY,
    event_id VARCHAR(128) NOT NULL UNIQUE,
    tenant_id VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency VARCHAR(10) NOT NULL,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(32) NOT NULL,
    journal_entry_id UUID,
    failure_reason TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    received_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_webhook_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id)
);

CREATE INDEX idx_webhook_event_id ON webhook_inbox (event_id);
CREATE INDEX idx_webhook_tenant ON webhook_inbox (tenant_id);

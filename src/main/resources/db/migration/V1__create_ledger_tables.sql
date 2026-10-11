-- Migration V1: Core Journal Entry and Line tables
CREATE TABLE journal_entry (
    id UUID PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL,
    entry_date DATE NOT NULL,
    description VARCHAR(255) NOT NULL,
    posted_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_by_entry_id UUID,
    reversal_of_entry_id UUID,
    CONSTRAINT fk_reversed_by FOREIGN KEY (reversed_by_entry_id) REFERENCES journal_entry (id),
    CONSTRAINT fk_reversal_of FOREIGN KEY (reversal_of_entry_id) REFERENCES journal_entry (id)
);

CREATE INDEX idx_journal_entry_tenant_date ON journal_entry (tenant_id, entry_date);
CREATE INDEX idx_journal_entry_reversal_of ON journal_entry (reversal_of_entry_id);

CREATE TABLE journal_entry_line (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL,
    tenant_id VARCHAR(64) NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    direction VARCHAR(10) NOT NULL,
    line_order INT NOT NULL,
    CONSTRAINT fk_line_journal_entry FOREIGN KEY (journal_entry_id) REFERENCES journal_entry (id) ON DELETE CASCADE,
    CONSTRAINT chk_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_journal_line_tenant_account ON journal_entry_line (tenant_id, account_code);
CREATE INDEX idx_journal_line_entry_id ON journal_entry_line (journal_entry_id);

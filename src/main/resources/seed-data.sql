-- ============================================================================
-- Sample Accounting Seed Data for Multi-Tenant Accounting Ledger
-- ============================================================================

-- ============================================================================
-- 1. Tenant 'acme-corp' Journal Entries
-- ============================================================================

-- Entry 1: Initial Founder Equity Funding (100,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-111111111111', 'acme-corp', '2026-09-01', 'Initial founder capital deposit', '2026-09-01 09:00:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-1111111111a1', '11111111-1111-1111-1111-111111111111', 'acme-corp', '1000-BANK', 100000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-1111111111a2', '11111111-1111-1111-1111-111111111111', 'acme-corp', '3000-EQUITY', 100000.0000, 'CREDIT', 2);

-- Entry 2: Client Billed for Software Services (25,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-222222222222', 'acme-corp', '2026-09-10', 'Invoice #INV-2026-001 issued to Client Alpha', '2026-09-10 11:30:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-2222222222a1', '11111111-1111-1111-1111-222222222222', 'acme-corp', '1200-RECEIVABLES', 25000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-2222222222a2', '11111111-1111-1111-1111-222222222222', 'acme-corp', '4000-SALES', 25000.0000, 'CREDIT', 2);

-- Entry 3: Client Alpha Paid Invoice #INV-2026-001 in Full (25,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-333333333333', 'acme-corp', '2026-09-15', 'Settlement received for invoice #INV-2026-001', '2026-09-15 14:15:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-3333333333a1', '11111111-1111-1111-1111-333333333333', 'acme-corp', '1000-BANK', 25000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-3333333333a2', '11111111-1111-1111-1111-333333333333', 'acme-corp', '1200-RECEIVABLES', 25000.0000, 'CREDIT', 2);

-- Entry 4: Office Rent & Facility Expense (8,500 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-444444444444', 'acme-corp', '2026-09-20', 'September office rent payment', '2026-09-20 10:00:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-4444444444a1', '11111111-1111-1111-1111-444444444444', 'acme-corp', '5000-EXPENSES', 8500.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-4444444444a2', '11111111-1111-1111-1111-444444444444', 'acme-corp', '1000-BANK', 8500.0000, 'CREDIT', 2);

-- Entry 5: Cloud Infrastructure Subscriptions (1,200 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-555555555555', 'acme-corp', '2026-09-25', 'Monthly cloud hosting fee', '2026-09-25 16:45:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-5555555555a1', '11111111-1111-1111-1111-555555555555', 'acme-corp', '5100-SOFTWARE', 1200.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-5555555555a2', '11111111-1111-1111-1111-555555555555', 'acme-corp', '1000-BANK', 1200.0000, 'CREDIT', 2);

-- Entry 6 & 7: Reversal Example Pair (15,000 INR Erroneous Purchase + Offset Reversal)
-- Original Entry 6:
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-666666666666', 'acme-corp', '2026-09-28', 'Erroneous equipment purchase invoice', '2026-09-28 09:30:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-6666666666a1', '11111111-1111-1111-1111-666666666666', 'acme-corp', '1500-EQUIPMENT', 15000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-6666666666a2', '11111111-1111-1111-1111-666666666666', 'acme-corp', '1000-BANK', 15000.0000, 'CREDIT', 2);

-- Reversing Entry 7 (Debits and Credits Swapped):
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('11111111-1111-1111-1111-777777777777', 'acme-corp', '2026-09-29', 'Reversal of entry 11111111-1111-1111-1111-666666666666 - Erroneous equipment purchase invoice', '2026-09-29 10:00:00+00', NULL, '11111111-1111-1111-1111-666666666666');

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-7777777777a1', '11111111-1111-1111-1111-777777777777', 'acme-corp', '1500-EQUIPMENT', 15000.0000, 'CREDIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('11111111-1111-1111-1111-7777777777a2', '11111111-1111-1111-1111-777777777777', 'acme-corp', '1000-BANK', 15000.0000, 'DEBIT', 2);

-- Link Entry 6 back to Entry 7 now that Entry 7 exists:
UPDATE journal_entry
SET reversed_by_entry_id = '11111111-1111-1111-1111-777777777777'
WHERE id = '11111111-1111-1111-1111-666666666666';

-- ============================================================================
-- 2. Tenant 'globex-corp' Journal Entries (Multi-Tenancy Isolation Demo)
-- ============================================================================

-- Entry 8: Globex Seed Capital Investment (500,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('22222222-2222-2222-2222-111111111111', 'globex-corp', '2026-09-05', 'Seed equity funding round', '2026-09-05 08:30:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-1111111111a1', '22222222-2222-2222-2222-111111111111', 'globex-corp', '1000-BANK', 500000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-1111111111a2', '22222222-2222-2222-2222-111111111111', 'globex-corp', '3000-EQUITY', 500000.0000, 'CREDIT', 2);

-- Entry 9: Globex Product Export Sale (75,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('22222222-2222-2222-2222-222222222222', 'globex-corp', '2026-09-18', 'Export customer shipment payment', '2026-09-18 12:00:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-2222222222a1', '22222222-2222-2222-2222-222222222222', 'globex-corp', '1000-BANK', 75000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-2222222222a2', '22222222-2222-2222-2222-222222222222', 'globex-corp', '4000-SALES', 75000.0000, 'CREDIT', 2);

-- Entry 10: Manufacturing Raw Material Inventory (40,000 INR)
INSERT INTO journal_entry (id, tenant_id, entry_date, description, posted_at, reversed_by_entry_id, reversal_of_entry_id)
VALUES ('22222222-2222-2222-2222-333333333333', 'globex-corp', '2026-09-22', 'Raw material shipment invoice', '2026-09-22 15:00:00+00', NULL, NULL);

INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-3333333333a1', '22222222-2222-2222-2222-333333333333', 'globex-corp', '1300-INVENTORY', 40000.0000, 'DEBIT', 1);
INSERT INTO journal_entry_line (id, journal_entry_id, tenant_id, account_code, amount, direction, line_order)
VALUES ('22222222-2222-2222-2222-3333333333a2', '22222222-2222-2222-2222-333333333333', 'globex-corp', '1000-BANK', 40000.0000, 'CREDIT', 2);

-- ============================================================================
-- 3. Idempotency Sample Records
-- ============================================================================
INSERT INTO idempotency_record (id, tenant_id, idempotency_key, request_hash, status, response_status, response_body, created_at, completed_at)
VALUES (
    '33333333-3333-3333-3333-111111111111',
    'acme-corp',
    'demo-seed-key-001',
    'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90',
    'COMPLETED',
    201,
    '{"id":"11111111-1111-1111-1111-111111111111","tenantId":"acme-corp","totalDebits":100000.00,"totalCredits":100000.00}',
    '2026-09-01 09:00:00+00',
    '2026-09-01 09:00:01+00'
);

-- ============================================================================
-- 4. Webhook Inbox Sample Records
-- ============================================================================
INSERT INTO webhook_inbox (id, event_id, tenant_id, amount, currency, settled_at, status, journal_entry_id, retry_count, received_at)
VALUES (
    '44444444-4444-4444-4444-111111111111',
    'evt_stripe_payment_seed_1001',
    'acme-corp',
    25000.0000,
    'INR',
    '2026-09-15 14:15:00+00',
    'PROCESSED',
    '11111111-1111-1111-1111-333333333333',
    0,
    '2026-09-15 14:15:01+00'
);

INSERT INTO webhook_inbox (id, event_id, tenant_id, amount, currency, settled_at, status, journal_entry_id, retry_count, received_at)
VALUES (
    '44444444-4444-4444-4444-222222222222',
    'evt_razorpay_export_seed_2002',
    'globex-corp',
    75000.0000,
    'INR',
    '2026-09-18 12:00:00+00',
    'PROCESSED',
    '22222222-2222-2222-2222-222222222222',
    0,
    '2026-09-18 12:00:01+00'
);

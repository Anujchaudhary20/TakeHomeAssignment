package com.ledger.common.exception;

public class UnbalancedJournalEntryException extends RuntimeException {
    public UnbalancedJournalEntryException(String message) {
        super(message);
    }
}

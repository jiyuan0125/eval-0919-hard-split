package com.ledger.config;

public class ImmutableWriteAttemptException extends RuntimeException {

    public ImmutableWriteAttemptException(String sql) {
        super("append-only table cannot be updated/deleted: " + sql);
    }
}

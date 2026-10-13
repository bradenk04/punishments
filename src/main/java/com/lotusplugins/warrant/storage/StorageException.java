package com.lotusplugins.warrant.storage;

public final class StorageException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public StorageException(String operation, Exception cause) {
        super("Could not " + operation, cause);
    }
}

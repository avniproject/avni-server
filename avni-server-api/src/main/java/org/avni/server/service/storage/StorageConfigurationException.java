package org.avni.server.service.storage;

public class StorageConfigurationException extends RuntimeException {
    public StorageConfigurationException(String message) {
        super(message);
    }

    public StorageConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

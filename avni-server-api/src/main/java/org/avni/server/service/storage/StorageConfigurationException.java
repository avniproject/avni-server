package org.avni.server.service.storage;

/**
 * Thrown when per-org storage routing is misconfigured (unknown target name, malformed target
 * descriptor, or a missing/undecryptable credential). Routing must fail <b>safe and loud</b>
 * rather than silently fall back to the default backend, so a misconfigured MODEL target never
 * silently writes/serves from Avni's default S3.
 */
public class StorageConfigurationException extends RuntimeException {
    public StorageConfigurationException(String message) {
        super(message);
    }

    public StorageConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}

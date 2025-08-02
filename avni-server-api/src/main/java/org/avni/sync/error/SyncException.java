package org.avni.sync.error;

/**
 * Custom exception for sync-related errors.
 * Provides structured error handling for various sync failure scenarios.
 */
public class SyncException extends RuntimeException {
    
    public static final String NETWORK_ERROR = "NETWORK_ERROR";
    public static final String AUTH_ERROR = "AUTH_ERROR";
    public static final String DATA_ERROR = "DATA_ERROR";
    public static final String MEDIA_ERROR = "MEDIA_ERROR";
    public static final String PERSISTENCE_ERROR = "PERSISTENCE_ERROR";
    public static final String TIMEOUT_ERROR = "TIMEOUT_ERROR";
    public static final String SERVER_ERROR = "SERVER_ERROR";
    public static final String CLIENT_ERROR = "CLIENT_ERROR";
    public static final String UNKNOWN_ERROR = "UNKNOWN_ERROR";
    
    private final String errorCode;
    private final boolean retryable;
    
    public SyncException(String message) {
        super(message);
        this.errorCode = UNKNOWN_ERROR;
        this.retryable = false;
    }
    
    public SyncException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = UNKNOWN_ERROR;
        this.retryable = false;
    }
    
    public SyncException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = isRetryableError(errorCode);
    }
    
    public SyncException(String message, Throwable cause, String errorCode) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = isRetryableError(errorCode);
    }
    
    public SyncException(String message, String errorCode, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public SyncException(String message, Throwable cause, String errorCode, boolean retryable) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    private static boolean isRetryableError(String errorCode) {
        return switch (errorCode) {
            case NETWORK_ERROR, TIMEOUT_ERROR, SERVER_ERROR -> true;
            case AUTH_ERROR, DATA_ERROR, CLIENT_ERROR -> false;
            default -> false;
        };
    }
    
    // Static factory methods for common error types
    public static SyncException networkError(String message) {
        return new SyncException(message, NETWORK_ERROR);
    }
    
    public static SyncException networkError(String message, Throwable cause) {
        return new SyncException(message, cause, NETWORK_ERROR);
    }
    
    public static SyncException authError(String message) {
        return new SyncException(message, AUTH_ERROR);
    }
    
    public static SyncException authError(String message, Throwable cause) {
        return new SyncException(message, cause, AUTH_ERROR);
    }
    
    public static SyncException dataError(String message) {
        return new SyncException(message, DATA_ERROR);
    }
    
    public static SyncException dataError(String message, Throwable cause) {
        return new SyncException(message, cause, DATA_ERROR);
    }
    
    public static SyncException mediaError(String message) {
        return new SyncException(message, MEDIA_ERROR);
    }
    
    public static SyncException mediaError(String message, Throwable cause) {
        return new SyncException(message, cause, MEDIA_ERROR);
    }
    
    public static SyncException persistenceError(String message) {
        return new SyncException(message, PERSISTENCE_ERROR);
    }
    
    public static SyncException persistenceError(String message, Throwable cause) {
        return new SyncException(message, cause, PERSISTENCE_ERROR);
    }
    
    public static SyncException timeoutError(String message) {
        return new SyncException(message, TIMEOUT_ERROR);
    }
    
    public static SyncException timeoutError(String message, Throwable cause) {
        return new SyncException(message, cause, TIMEOUT_ERROR);
    }
    
    public static SyncException serverError(String message) {
        return new SyncException(message, SERVER_ERROR);
    }
    
    public static SyncException serverError(String message, Throwable cause) {
        return new SyncException(message, cause, SERVER_ERROR);
    }
    
    public static SyncException clientError(String message) {
        return new SyncException(message, CLIENT_ERROR);
    }
    
    public static SyncException clientError(String message, Throwable cause) {
        return new SyncException(message, cause, CLIENT_ERROR);
    }
    
    @Override
    public String toString() {
        return "SyncException{" +
                "errorCode='" + errorCode + '\'' +
                ", retryable=" + retryable +
                ", message='" + getMessage() + '\'' +
                '}';
    }
}
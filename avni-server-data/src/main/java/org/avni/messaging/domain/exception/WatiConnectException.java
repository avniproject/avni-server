package org.avni.messaging.domain.exception;

import org.springframework.dao.DataAccessException;

/**
 * Thrown when the Wati REST API returns an error or is unreachable.
 */
public class WatiConnectException extends DataAccessException {
    public WatiConnectException(String msg) {
        super(msg);
    }

    public WatiConnectException(String msg, Throwable cause) {
        super(msg, cause);
    }
}

package org.avni.messaging.domain.exception;

/**
 * Thrown when an org is expected to use Wati but no Wati external_system_config
 * row exists under the platform org.
 */
public class WatiNotConfiguredException extends Exception {
    public WatiNotConfiguredException(String message) {
        super(message);
    }
}

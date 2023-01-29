package org.avni.messaging.domain.exception;

public class GlificGroupMessageFailureException extends RuntimeException {
    private String contactIdFailedAt;
    private String message;

    public GlificGroupMessageFailureException(String contactIdFailedAt, String message) {
        this.contactIdFailedAt = contactIdFailedAt;
        this.message = message;
    }

    public String getContactIdFailedAt() {
        return contactIdFailedAt;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}

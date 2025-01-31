package org.avni.messaging.contract;

import org.avni.messaging.domain.MessageDeliveryStatus;

public class SendMessageResponse {
    MessageDeliveryStatus messageDeliveryStatus;
    String errorMessage;

    public SendMessageResponse() {
    }

    public SendMessageResponse(MessageDeliveryStatus messageDeliveryStatus, String errorMessage) {
        this.messageDeliveryStatus = messageDeliveryStatus;
        this.errorMessage = errorMessage;
    }

    public MessageDeliveryStatus getMessageDeliveryStatus() {
        return messageDeliveryStatus;
    }

    public void setMessageDeliveryStatus(MessageDeliveryStatus messageDeliveryStatus) {
        this.messageDeliveryStatus = messageDeliveryStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

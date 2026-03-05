package org.avni.messaging.service;

import org.avni.messaging.domain.MessageDeliveryStatus;

public class PhoneNumberNotAvailableOrIncorrectException extends Exception {
    private final MessageDeliveryStatus deliveryStatus;

    public PhoneNumberNotAvailableOrIncorrectException(String message, MessageDeliveryStatus deliveryStatus) {
        super(message);
        this.deliveryStatus = deliveryStatus;
    }

    public MessageDeliveryStatus getDeliveryStatus() {
        return deliveryStatus;
    }
}

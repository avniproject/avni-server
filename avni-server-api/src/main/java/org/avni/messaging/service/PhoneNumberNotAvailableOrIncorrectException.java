package org.avni.messaging.service;

public class PhoneNumberNotAvailableOrIncorrectException extends Exception {
    public PhoneNumberNotAvailableOrIncorrectException() {
    }

    public PhoneNumberNotAvailableOrIncorrectException(String message) {
        super(message);
    }
}

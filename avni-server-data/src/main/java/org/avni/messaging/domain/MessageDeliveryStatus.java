package org.avni.messaging.domain;

public enum MessageDeliveryStatus {
    NotSent,
    NotSentNoPhoneNumberInAvni,
    NotSentInvalidPhoneNumberInAvni,
    PartiallySent,
    Sent,
    Failed
}

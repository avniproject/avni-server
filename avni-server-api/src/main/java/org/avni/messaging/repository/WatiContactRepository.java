package org.avni.messaging.repository;

import org.avni.messaging.domain.MessageDeliveryStatus;
import org.avni.messaging.service.PhoneNumberNotAvailableOrIncorrectException;
import org.avni.server.util.PhoneNumberUtil;
import org.avni.server.util.RegionUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Repository;

/**
 * Wati contact resolution — unlike Glific, Wati does not require registering a contact
 * before sending a message. The phone number itself is the contact identifier.
 *
 * Wati's send-template API takes the phone number directly in the URL path:
 *   POST /api/v1/sendTemplateMessage/{whatsappNumber}
 *
 * So this repository simply validates and formats the phone number (same E.164-without-plus
 * format used by Glific) and returns it as the externalId stored on MessageReceiver.
 */
@Repository
@Lazy
public class WatiContactRepository {

    /**
     * Returns the phone number in Wati-compatible format (E.164 without '+', e.g. "919876543210")
     * to be stored as MessageReceiver.externalId.
     *
     * No API call is made — phone validation is done by the caller (MessageReceiverService)
     * before this method is invoked.
     *
     * Signature mirrors GlificContactRepository.getOrCreateContact() so the branch in
     * MessageReceiverService stays symmetric.
     */
    public String getOrCreateContact(String phoneNumber, String fullName) throws PhoneNumberNotAvailableOrIncorrectException {
        if (!PhoneNumberUtil.isValidPhoneNumber(phoneNumber, RegionUtil.getCurrentUserRegion())) {
            throw new PhoneNumberNotAvailableOrIncorrectException(
                    "Invalid phone number for Wati contact: " + fullName,
                    MessageDeliveryStatus.NotSentInvalidPhoneNumberInAvni);
        }
        // Reuse the same format as Glific: E.164 without '+' e.g. "919876543210"
        return PhoneNumberUtil.getPhoneNumberInGlificFormat(phoneNumber, RegionUtil.getCurrentUserRegion());
    }
}

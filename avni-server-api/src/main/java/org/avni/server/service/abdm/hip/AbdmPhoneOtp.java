package org.avni.server.service.abdm.hip;

import org.avni.server.service.PhoneNumberVerificationService;
import org.avni.server.web.request.PhoneNumberVerificationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// A one-time code by text message, through the organisation's Msg91 settings (PhoneNumberVerificationService), which
// generates, sends and checks the code itself: Avni never holds it. In a dev server that service sends nothing and
// accepts 1234, as it does for phone verification. Never logs a phone number or a code.
@Component
public class AbdmPhoneOtp {
    private static final Logger logger = LoggerFactory.getLogger(AbdmPhoneOtp.class);
    private static final String LENGTH = "6";

    private final PhoneNumberVerificationService phoneVerification;

    public AbdmPhoneOtp(PhoneNumberVerificationService phoneVerification) {
        this.phoneVerification = phoneVerification;
    }

    public boolean send(String phone) {
        try {
            return phoneVerification.sendOTP(new PhoneNumberVerificationRequest(phone, null, LENGTH)).isSuccess();
        } catch (Exception e) {
            logger.warn("ABDM link code not sent: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public boolean verify(String phone, String code) {
        try {
            return phoneVerification.verifyOTP(new PhoneNumberVerificationRequest(phone, code, LENGTH)).isSuccess();
        } catch (Exception e) {
            logger.info("ABDM link code not accepted: {}", e.getClass().getSimpleName());
            return false;
        }
    }
}

package org.avni.server.service.abdm.hip;

import org.avni.server.service.PhoneNumberVerificationService;
import org.avni.server.web.request.PhoneNumberVerificationRequest;
import org.avni.server.web.response.PhoneNumberVerificationResponse;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmPhoneOtpTest {
    private final PhoneNumberVerificationService service = mock(PhoneNumberVerificationService.class);
    private final AbdmPhoneOtp otp = new AbdmPhoneOtp(service);

    @Test
    public void theCodeTypedGoesToTheCheckAsTyped() throws Exception {
        when(service.verifyOTP(any())).thenReturn(new PhoneNumberVerificationResponse(true, null));

        assertTrue(otp.verify("9000000000", "654321"));

        ArgumentCaptor<PhoneNumberVerificationRequest> asked = ArgumentCaptor.forClass(PhoneNumberVerificationRequest.class);
        verify(service).verifyOTP(asked.capture());
        assertEquals("654321", asked.getValue().getOtp());
    }

    @Test
    public void aCodeTheServiceRejectsIsNotAccepted() throws Exception {
        when(service.verifyOTP(any())).thenReturn(new PhoneNumberVerificationResponse(false, null));

        assertFalse(otp.verify("9000000000", "123456"));
    }

    @Test
    public void aFailureToSendIsNotASend() throws Exception {
        when(service.sendOTP(any())).thenThrow(new RuntimeException("down"));

        assertFalse(otp.send("9000000000"));
    }
}

package org.avni.server.service.abdm.hip;

import org.avni.server.service.PhoneNumberVerificationService;
import org.avni.server.web.request.PhoneNumberVerificationRequest;
import org.avni.server.web.response.PhoneNumberVerificationResponse;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AbdmPhoneOtpTest {
    private final PhoneNumberVerificationService service = mock(PhoneNumberVerificationService.class);

    private String codeChecked(boolean isDev, boolean msg91InDev, String typed) throws Exception {
        when(service.verifyOTP(any())).thenReturn(new PhoneNumberVerificationResponse(true, null));
        new AbdmPhoneOtp(service, isDev, msg91InDev).verify("9000000000", typed);
        ArgumentCaptor<PhoneNumberVerificationRequest> asked = ArgumentCaptor.forClass(PhoneNumberVerificationRequest.class);
        verify(service).verifyOTP(asked.capture());
        return asked.getValue().getOtp();
    }

    @Test
    public void aDevServerThatTextsNothingTakesSixDigitsForItsFixedCode() throws Exception {
        assertEquals("1234", codeChecked(true, false, "123456"));
    }

    @Test
    public void anyOtherCodeGoesToTheCheckAsTyped() throws Exception {
        assertEquals("654321", codeChecked(true, false, "654321"));
    }

    @Test
    public void aDevServerThatTextsRealCodesChecksSixDigitsAsTyped() throws Exception {
        assertEquals("123456", codeChecked(true, true, "123456"));
    }

    @Test
    public void aLiveServerChecksSixDigitsAsTyped() throws Exception {
        assertEquals("123456", codeChecked(false, false, "123456"));
    }
}

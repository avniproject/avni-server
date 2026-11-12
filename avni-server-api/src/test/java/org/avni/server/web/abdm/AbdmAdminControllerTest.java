package org.avni.server.web.abdm;

import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.eka.EkaCallException;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class AbdmAdminControllerTest {
    private EkaAbdmClient ekaClient;
    private AbdmAdminController controller;

    @Before
    public void setUp() {
        ekaClient = mock(EkaAbdmClient.class);
        when(ekaClient.baseUrl()).thenReturn("https://api.dev.eka.care");
        AbdmConfig cfg = new AbdmConfig(new MockEnvironment(), "https://demo.example.org/", "TANUH", "TANUH Health Centre",
                "TANUH-HIU", "", "", "@sbx", "", "", "Dr. Demo", "log");
        controller = new AbdmAdminController(mock(AccessControlService.class), cfg, ekaClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void statusReportsTheEkaTokenTheEkaAddressAndTheWebhookToGiveEka() {
        Map<String, Object> status = controller.status();

        assertEquals("https://api.dev.eka.care", status.get("ekaBaseUrl"));
        assertEquals("https://demo.example.org/api/v3/eka/webhook", status.get("webhookUrl"));
        assertEquals(true, ((Map<String, Object>) status.get("session")).get("ok"));
        assertFalse(status.containsKey("mode"));
        verify(ekaClient).checkToken();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void withoutAnEkaTokenTheStatusSaysWhy() {
        doThrow(new EkaCallException(0, null, "Eka login failed: Eka login failed with HTTP 401")).when(ekaClient).checkToken();

        Map<String, Object> session = (Map<String, Object>) controller.status().get("session");

        assertEquals(false, session.get("ok"));
        assertTrue(((String) session.get("error")).contains("Eka login failed"));
    }
}

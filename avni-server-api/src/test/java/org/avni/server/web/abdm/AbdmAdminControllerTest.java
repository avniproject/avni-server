package org.avni.server.web.abdm;

import org.avni.server.service.abdm.AbdmConfig;
import org.avni.server.service.abdm.AbdmMessageLog;
import org.avni.server.service.abdm.eka.EkaAbdmClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayClient;
import org.avni.server.service.abdm.gateway.AbdmGatewayException;
import org.avni.server.service.abdm.gateway.AbdmSessionService;
import org.avni.server.service.abdm.hip.AbdmAutoLink;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon: never merged.
public class AbdmAdminControllerTest {
    private AbdmSessionService session;
    private EkaAbdmClient ekaClient;
    private final AbdmAutoLink autoLink = new AbdmAutoLink(true);

    @Before
    public void setUp() {
        session = mock(AbdmSessionService.class);
        ekaClient = mock(EkaAbdmClient.class);
        when(ekaClient.baseUrl()).thenReturn("https://api.dev.eka.care");
    }

    private AbdmAdminController controller(String mode) {
        AbdmConfig cfg = new AbdmConfig(new MockEnvironment(), mode, "https://dev.abdm.gov.in", "/api/hiecm/gateway/v3/sessions",
                "/api/hiecm/gateway/v3/certs", "sbx", "client", "", "log", "https://demo.example.org/", "TANUH", "TANUH Health Centre",
                "TANUH-HIU", "", "", "123456", "@sbx", 300, "https://facilitysbx.abdm.gov.in", "", "", "https://hpridsbx.abdm.gov.in", "",
                "", "", "Dr. Demo", true, "log");
        return new AbdmAdminController(mock(AccessControlService.class), cfg, session, mock(AbdmGatewayClient.class),
                mock(AbdmMessageLog.class), ekaClient, autoLink);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ekaModeReportsTheEkaTokenTheEkaAddressAndTheWebhookToGiveEka() {
        Map<String, Object> status = controller("eka").status();

        assertEquals("eka", status.get("mode"));
        assertEquals("https://api.dev.eka.care", status.get("gatewayBaseUrl"));
        assertEquals("https://demo.example.org/api/v3/eka/webhook", status.get("webhookUrl"));
        assertEquals(true, ((Map<String, Object>) status.get("session")).get("ok"));
        verify(ekaClient).checkToken();
        verifyNoInteractions(session);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ekaModeWithoutATokenSaysWhy() {
        doThrow(new AbdmGatewayException(0, null, "Eka login failed: Eka login failed with HTTP 401")).when(ekaClient).checkToken();

        Map<String, Object> session = (Map<String, Object>) controller("eka").status().get("session");

        assertEquals(false, session.get("ok"));
        assertTrue(((String) session.get("error")).contains("Eka login failed"));
    }

    // Linking on save, shown on the Status tab and switched there while the server runs (HIP_INTI_LINK_505).
    @Test
    public void linkingOnSaveIsReportedAndCanBeSwitched() {
        AbdmAdminController controller = controller("eka");
        assertEquals(true, controller.status().get("autoLink"));

        assertEquals(Map.of("autoLink", false), controller.setAutoLink(Map.of("on", false)));

        assertFalse(autoLink.isOn());
        assertEquals(false, controller.status().get("autoLink"));
    }

    @Test
    public void sandboxModeIsUnchanged() {
        Map<String, Object> status = controller("sandbox").status();

        assertEquals("sandbox", status.get("mode"));
        assertEquals("https://dev.abdm.gov.in", status.get("gatewayBaseUrl"));
        assertFalse(status.containsKey("webhookUrl"));
        verify(session).accessToken();
        verifyNoInteractions(ekaClient);
    }
}

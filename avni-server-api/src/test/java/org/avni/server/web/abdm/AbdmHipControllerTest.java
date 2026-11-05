package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.eka.EkaHipService;
import org.avni.server.service.abdm.hip.AbdmHipLinkingService;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon.
public class AbdmHipControllerTest {
    private final AbdmHipLinkingService linking = mock(AbdmHipLinkingService.class);
    private final EkaHipService eka = mock(EkaHipService.class);
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final AbdmHipController controller = new AbdmHipController(linking, eka, accessControlService);

    @Test
    public void addingSampleRecordsChecksThePrivilegeThenPassesTheTypesOn() {
        List<Map<String, Object>> view = List.of(Map.of("reference", "sample-Prescription-s1"));
        when(eka.addSampleRecords("s1", List.of("Prescription"))).thenReturn(view);

        List<Map<String, Object>> answer = controller.addSampleRecords("s1", Map.of("hiTypes", List.of("Prescription")));

        assertSame(view, answer);
        InOrder order = inOrder(accessControlService, eka);
        order.verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        order.verify(eka).addSampleRecords("s1", List.of("Prescription"));
    }

    @Test
    public void noBodyMeansEveryType() {
        controller.addSampleRecords("s1", null);

        verify(eka).addSampleRecords("s1", null);
    }

    @Test
    public void addingSampleRecordsIsRefusedWithoutTheAdminPrivilege() {
        doThrow(new RuntimeException("no privilege")).when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        try {
            controller.addSampleRecords("s1", null);
        } catch (RuntimeException expected) {
            verify(eka, never()).addSampleRecords(anyString(), any());
            return;
        }
        throw new AssertionError("expected a refusal");
    }
}

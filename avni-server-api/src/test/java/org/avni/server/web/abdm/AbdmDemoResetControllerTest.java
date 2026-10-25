package org.avni.server.web.abdm;

import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.abdm.AbdmOrgContext;
import org.avni.server.service.accessControl.AccessControlService;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

// DEMO-ONLY abdm-hackathon.
public class AbdmDemoResetControllerTest {
    private final AccessControlService accessControlService = mock(AccessControlService.class);
    private final NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
    private final AbdmOrgContext orgContext = mock(AbdmOrgContext.class);
    private final AbdmDemoResetController controller = new AbdmDemoResetController(accessControlService, jdbc, orgContext);

    @Test
    public void clearsEveryAbdmTableOfTheOrganisationChildrenFirst() {
        when(orgContext.organisationId()).thenReturn(4L);
        when(jdbc.update(anyString(), any(MapSqlParameterSource.class))).thenReturn(2);

        Map<String, Object> result = controller.reset();

        InOrder order = inOrder(accessControlService, jdbc);
        order.verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<MapSqlParameterSource> params = ArgumentCaptor.forClass(MapSqlParameterSource.class);
        order.verify(jdbc, times(7)).update(sql.capture(), params.capture());
        List<String> tables = sql.getAllValues().stream().map(s -> s.split(" ")[2]).toList();
        assertEquals(List.of("abdm_health_information", "abdm_data_flow", "abdm_consent", "abdm_link_request",
                "abdm_care_context", "abdm_patient_link", "abdm_message"), tables);
        sql.getAllValues().forEach(s -> assertTrue(s, s.endsWith("where organisation_id = :org")));
        params.getAllValues().forEach(p -> assertEquals(4L, p.getValue("org")));
        assertEquals(14, result.get("rowsDeleted"));
    }

    @Test
    public void refusesWithoutTheAdminPrivilege() {
        doThrow(new RuntimeException("no privilege")).when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        try {
            controller.reset();
        } catch (RuntimeException expected) {
            verifyNoInteractions(jdbc);
            return;
        }
        throw new AssertionError("expected a refusal");
    }
}

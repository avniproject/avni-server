package org.avni.server.web;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.domain.UserContext;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.orgdata.DeleteOrgDataJobService;
import org.avni.server.service.BundleService;
import org.avni.server.service.ImplementationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.validation.ValidationException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;

public class ImplementationControllerTest {
    @Mock
    private ImplementationService implementationService;
    @Mock
    private AccessControlService accessControlService;
    @Mock
    private BundleService bundleService;
    @Mock
    private DeleteOrgDataJobService deleteOrgDataJobService;

    private ImplementationController controller;
    private Organisation organisation;

    @Before
    public void setUp() {
        openMocks(this);
        controller = new ImplementationController(implementationService, accessControlService, bundleService, deleteOrgDataJobService);
        organisation = new Organisation();
        organisation.setUuid("org-uuid");
        UserContext userContext = new UserContext();
        userContext.setOrganisation(organisation);
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void startChecksFirstThenReturnsTheJobUuidWithoutDeletingInTheRequest() {
        when(deleteOrgDataJobService.start(true, false)).thenReturn("job-uuid");

        ResponseEntity<String> response = controller.startDeleteJob(true, false);

        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertEquals("job-uuid", response.getBody());
        InOrder inOrder = inOrder(implementationService, deleteOrgDataJobService);
        inOrder.verify(implementationService).assertCanDeleteImplementationData(true, false);
        inOrder.verify(deleteOrgDataJobService).start(true, false);
        verify(implementationService, never()).deleteImplementationData(anyBoolean(), anyBoolean());
    }

    @Test
    public void startRefusesWithTheSameMessageAndStartsNoJob() {
        doThrow(new ValidationException("Super admin cannot delete implementation data"))
                .when(implementationService).assertCanDeleteImplementationData(false, false);

        ResponseEntity<String> response = controller.startDeleteJob(false, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Super admin cannot delete implementation data", response.getBody());
        verify(deleteOrgDataJobService, never()).start(anyBoolean(), anyBoolean());
    }

    @Test
    public void startWithoutPrivilegeIsRefusedAndStartsNoJob() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.EditOrganisationConfiguration))
                .when(implementationService).assertCanDeleteImplementationData(false, false);

        assertThrows(AvniAccessException.class, () -> controller.startDeleteJob(false, false));
        verify(deleteOrgDataJobService, never()).start(anyBoolean(), anyBoolean());
    }

    @Test
    public void statusIsTheLatestDeletionJobOfTheCallersOrganisation() {
        BatchJobStatus status = new BatchJobStatus("COMPLETED", null, null, null, "", "COMPLETED");
        when(deleteOrgDataJobService.getLatestJobStatus(organisation)).thenReturn(status);

        assertSame(status, controller.deleteJobStatus());
        verify(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
    }

    @Test
    public void statusWithoutPrivilegeIsRefused() {
        doThrow(AvniAccessException.createNoPrivilegeException(PrivilegeType.EditOrganisationConfiguration))
                .when(accessControlService).checkPrivilege(PrivilegeType.EditOrganisationConfiguration);

        assertThrows(AvniAccessException.class, () -> controller.deleteJobStatus());
        verify(deleteOrgDataJobService, never()).getLatestJobStatus(any());
    }

    @Test
    public void theSynchronousEndpointStillDeletesInTheRequest() {
        ResponseEntity<String> response = controller.delete(true, true);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(implementationService).deleteImplementationData(true, true);
        verify(deleteOrgDataJobService, never()).start(anyBoolean(), anyBoolean());
    }
}

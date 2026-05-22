package org.avni.server.web;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.dao.GroupRoleRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.OperationalSubjectType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.User;
import org.avni.server.service.FormMappingService;
import org.avni.server.service.FormService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.service.ResetSyncService;
import org.avni.server.service.SubjectTypeService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.webapp.SubjectTypeContractWeb;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SubjectTypeControllerAttendanceTest {

    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Mock
    private SubjectTypeService subjectTypeService;
    @Mock
    private GroupRoleRepository groupRoleRepository;
    @Mock
    private ResetSyncService resetSyncService;
    @Mock
    private FormService formService;
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AccessControlService accessControlService;

    private SubjectTypeController controller;

    @Before
    public void setUp() {
        initMocks(this);
        controller = new SubjectTypeController(subjectTypeRepository, operationalSubjectTypeRepository,
                subjectTypeService, groupRoleRepository, resetSyncService, formService, formMappingService,
                organisationConfigService, accessControlService);
        User auditUser = new User();
        auditUser.setUsername("audit");
        when(subjectTypeRepository.save(any(SubjectType.class))).thenAnswer(inv -> inv.getArgument(0));
        when(operationalSubjectTypeRepository.save(any(OperationalSubjectType.class))).thenAnswer(inv -> {
            OperationalSubjectType o = inv.getArgument(0);
            if (o.getCreatedBy() == null) o.setCreatedBy(auditUser);
            if (o.getLastModifiedBy() == null) o.setLastModifiedBy(auditUser);
            return o;
        });
        when(formMappingService.find(any(SubjectType.class))).thenReturn(null);
    }

    @Test
    public void saveValidatesAttendanceBeforePersistAndSeedsAfter() {
        SubjectTypeContractWeb request = baseGroupRequest();
        request.setAttendanceEnabled(true);

        controller.saveSubjectTypeForWeb(request);

        InOrder inOrder = inOrder(subjectTypeService, subjectTypeRepository);
        inOrder.verify(subjectTypeService).validateAttendanceEligibilityAndConfig(eq(null), eq(true), eq(true), eq(false));
        inOrder.verify(subjectTypeRepository).save(any(SubjectType.class));
        inOrder.verify(subjectTypeService).seedDefaultAttendanceTypeIfEnabling(any(SubjectType.class), eq(false));
    }

    @Test
    public void savePropagatesAttendanceEnabledIntoEntity() {
        SubjectTypeContractWeb request = baseGroupRequest();
        request.setAttendanceEnabled(true);

        controller.saveSubjectTypeForWeb(request);

        ArgumentCaptor<SubjectType> captor = ArgumentCaptor.forClass(SubjectType.class);
        verify(subjectTypeRepository).save(captor.capture());
        assertTrue(captor.getValue().isAttendanceEnabled());
    }

    @Test
    public void saveSkipsSeedWhenAttendanceDisabled() {
        SubjectTypeContractWeb request = baseGroupRequest();
        request.setAttendanceEnabled(false);

        controller.saveSubjectTypeForWeb(request);

        verify(subjectTypeService).seedDefaultAttendanceTypeIfEnabling(any(SubjectType.class), eq(false));
        ArgumentCaptor<SubjectType> captor = ArgumentCaptor.forClass(SubjectType.class);
        verify(subjectTypeRepository).save(captor.capture());
        assertTrue(!captor.getValue().isAttendanceEnabled());
    }

    @Test
    public void saveSkipsAttendanceWiringWhenNameAlreadyExists() {
        SubjectTypeContractWeb request = baseGroupRequest();
        request.setAttendanceEnabled(true);
        SubjectType existing = new SubjectType();
        existing.setName(request.getName());
        when(subjectTypeRepository.findByNameIgnoreCase(request.getName())).thenReturn(existing);

        controller.saveSubjectTypeForWeb(request);

        verify(subjectTypeService, never()).validateAttendanceEligibilityAndConfig(any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(subjectTypeService, never()).seedDefaultAttendanceTypeIfEnabling(any(SubjectType.class), anyBoolean());
    }

    @Test
    public void updateCapturesPreviousAttendanceFlagBeforeSeed() {
        SubjectType existingSubjectType = new SubjectType();
        existingSubjectType.setId(10L);
        existingSubjectType.setUuid("subj-type-uuid");
        existingSubjectType.setName("ExistingGroup");
        existingSubjectType.setType(Subject.Group);
        existingSubjectType.setGroup(true);
        existingSubjectType.setAttendanceEnabled(true);

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setId(20L);
        operationalSubjectType.setUuid("op-subj-type-uuid");
        operationalSubjectType.setName("ExistingGroup");
        operationalSubjectType.setSubjectType(existingSubjectType);
        User auditUser = new User();
        auditUser.setUsername("audit");
        operationalSubjectType.setCreatedBy(auditUser);
        operationalSubjectType.setLastModifiedBy(auditUser);

        when(operationalSubjectTypeRepository.findOne(20L)).thenReturn(operationalSubjectType);

        SubjectTypeContractWeb request = baseGroupRequest();
        request.setName("ExistingGroup");
        request.setAttendanceEnabled(true);

        controller.updateSubjectTypeForWeb(request, 20L);

        InOrder inOrder = inOrder(subjectTypeService);
        inOrder.verify(subjectTypeService).validateAttendanceEligibilityAndConfig(eq(existingSubjectType), eq(true), eq(true), eq(false));
        inOrder.verify(subjectTypeService).seedDefaultAttendanceTypeIfEnabling(eq(existingSubjectType), eq(true));
    }

    @Test
    public void updatePassesFalseToSeedWhenAttendanceWasPreviouslyDisabled() {
        SubjectType existingSubjectType = new SubjectType();
        existingSubjectType.setId(11L);
        existingSubjectType.setUuid("subj-type-uuid-2");
        existingSubjectType.setName("AnotherGroup");
        existingSubjectType.setType(Subject.Group);
        existingSubjectType.setGroup(true);
        existingSubjectType.setAttendanceEnabled(false);

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setId(21L);
        operationalSubjectType.setUuid("op-subj-type-uuid-2");
        operationalSubjectType.setName("AnotherGroup");
        operationalSubjectType.setSubjectType(existingSubjectType);
        User auditUser = new User();
        auditUser.setUsername("audit");
        operationalSubjectType.setCreatedBy(auditUser);
        operationalSubjectType.setLastModifiedBy(auditUser);

        when(operationalSubjectTypeRepository.findOne(21L)).thenReturn(operationalSubjectType);

        SubjectTypeContractWeb request = baseGroupRequest();
        request.setName("AnotherGroup");
        request.setAttendanceEnabled(true);

        controller.updateSubjectTypeForWeb(request, 21L);

        verify(subjectTypeService).seedDefaultAttendanceTypeIfEnabling(eq(existingSubjectType), eq(false));
    }

    @Test
    public void updateWithOmittedAttendanceFieldPreservesExistingValue() {
        SubjectType existingSubjectType = new SubjectType();
        existingSubjectType.setId(50L);
        existingSubjectType.setUuid("preserve-uuid");
        existingSubjectType.setName("PreserveGroup");
        existingSubjectType.setType(Subject.Group);
        existingSubjectType.setGroup(true);
        existingSubjectType.setAttendanceEnabled(true);

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setId(60L);
        operationalSubjectType.setUuid("op-preserve-uuid");
        operationalSubjectType.setName("PreserveGroup");
        operationalSubjectType.setSubjectType(existingSubjectType);
        User auditUser = new User();
        auditUser.setUsername("audit");
        operationalSubjectType.setCreatedBy(auditUser);
        operationalSubjectType.setLastModifiedBy(auditUser);
        when(operationalSubjectTypeRepository.findOne(60L)).thenReturn(operationalSubjectType);

        SubjectTypeContractWeb request = baseGroupRequest();
        request.setName("PreserveGroup");
        request.setAttendanceEnabled(null);

        controller.updateSubjectTypeForWeb(request, 60L);

        assertTrue(existingSubjectType.isAttendanceEnabled());
        verify(subjectTypeService).validateAttendanceEligibilityAndConfig(eq(existingSubjectType), eq(true), eq(true), eq(false));
        verify(subjectTypeService).seedDefaultAttendanceTypeIfEnabling(eq(existingSubjectType), eq(true));
    }

    @Test
    public void updateWithNullTypeReturnsBadRequestInsteadOfNpe() {
        SubjectType existingSubjectType = new SubjectType();
        existingSubjectType.setId(70L);
        existingSubjectType.setUuid("null-type-uuid");
        existingSubjectType.setName("AnyGroup");
        existingSubjectType.setType(Subject.Group);
        existingSubjectType.setGroup(true);

        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.setId(80L);
        operationalSubjectType.setUuid("op-null-type-uuid");
        operationalSubjectType.setName("AnyGroup");
        operationalSubjectType.setSubjectType(existingSubjectType);
        User auditUser = new User();
        auditUser.setUsername("audit");
        operationalSubjectType.setCreatedBy(auditUser);
        operationalSubjectType.setLastModifiedBy(auditUser);
        when(operationalSubjectTypeRepository.findOne(80L)).thenReturn(operationalSubjectType);

        SubjectTypeContractWeb request = new SubjectTypeContractWeb();
        request.setName("AnyGroup");
        request.setUUID("null-type-uuid");
        request.setType(null);
        request.setGroupRoles(java.util.Collections.emptyList());

        org.springframework.http.ResponseEntity response = controller.updateSubjectTypeForWeb(request, 80L);

        assertTrue(response.getStatusCode().is4xxClientError());
        verify(subjectTypeService, never()).validateAttendanceEligibilityAndConfig(any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(subjectTypeService, never()).seedDefaultAttendanceTypeIfEnabling(any(SubjectType.class), anyBoolean());
    }

    @Test
    public void fromOperationalSubjectTypeRoundTripsAttendanceEnabled() {
        SubjectType subjectType = new SubjectType();
        subjectType.setId(30L);
        subjectType.setUuid("subj-type-uuid-3");
        subjectType.setName("Group3");
        subjectType.setType(Subject.Group);
        subjectType.setGroup(true);
        subjectType.setAttendanceEnabled(true);

        OperationalSubjectType op = new OperationalSubjectType();
        op.setId(40L);
        op.setUuid("op-subj-type-uuid-3");
        op.setName("Group3");
        op.setSubjectType(subjectType);
        org.avni.server.domain.User user = new org.avni.server.domain.User();
        user.setUsername("u");
        op.setCreatedBy(user);
        op.setLastModifiedBy(user);

        SubjectTypeContractWeb result = SubjectTypeContractWeb.fromOperationalSubjectType(op, (FormMapping) null);

        assertTrue(result.isAttendanceEnabled());
    }

    private SubjectTypeContractWeb baseGroupRequest() {
        SubjectTypeContractWeb request = new SubjectTypeContractWeb();
        request.setName("NewGroup");
        request.setUUID("new-subj-type-uuid");
        request.setType(Subject.Group.toString());
        request.setGroupRoles(java.util.Collections.emptyList());
        return request;
    }
}

package org.avni.server.service;

import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.AvniJobRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.service.attendance.AttendanceConfigIncompleteException;
import org.avni.server.service.attendance.AttendanceTypeConfigKey;
import org.avni.server.service.batch.BatchJobService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.request.attendance.AttendanceTypeContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class SubjectTypeAttendanceValidationTest {

    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    @Mock
    private AvniJobRepository avniJobRepository;
    @Mock
    private ConceptService conceptService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private UserService userService;
    @Mock
    private LocationHierarchyService locationHierarchyService;
    @Mock
    private BatchJobService batchJobService;
    @Mock
    private AttendanceTypeRepository attendanceTypeRepository;

    private SubjectTypeService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new SubjectTypeService(subjectTypeRepository, operationalSubjectTypeRepository,
                null, null, null, null, avniJobRepository, conceptService, organisationConfigService,
                addressLevelTypeRepository, userService, locationHierarchyService, batchJobService,
                attendanceTypeRepository);
        when(subjectTypeRepository.save(any(SubjectType.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void rejectsAttendanceEnabledOnIndividualType() {
        SubjectTypeContract request = baseRequest();
        request.setAttendanceEnabled(true);
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(null);

        try {
            service.saveSubjectType(request);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("group"));
        }
        verify(subjectTypeRepository, never()).save(any(SubjectType.class));
    }

    @Test
    public void allowsAttendanceEnabledOnGroupType() {
        SubjectTypeContract request = baseRequest();
        request.setIsGroup(true);
        request.setAttendanceEnabled(true);
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(null);

        service.saveSubjectType(request);

        verify(subjectTypeRepository).save(any(SubjectType.class));
    }

    @Test
    public void seedsDefaultAttendanceTypeOnEnable() {
        SubjectTypeContract request = baseRequest();
        request.setIsGroup(true);
        request.setAttendanceEnabled(true);

        SubjectType existing = new SubjectType();
        existing.setId(1L);
        existing.setUuid(request.getUuid());
        existing.setGroup(true);
        existing.setAttendanceEnabled(false);
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(existing);
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(existing)).thenReturn(Collections.emptyList());

        service.saveSubjectType(request);

        ArgumentCaptor<AttendanceType> attendanceCaptor = ArgumentCaptor.forClass(AttendanceType.class);
        verify(attendanceTypeRepository, times(1)).save(attendanceCaptor.capture());
        assertEquals("Attendance", attendanceCaptor.getValue().getName());
        assertEquals(Integer.valueOf(1), attendanceCaptor.getValue().getSortOrder());
    }

    @Test
    public void doesNotReSeedWhenAlreadyEnabled() {
        SubjectTypeContract request = baseRequest();
        request.setIsGroup(true);
        request.setAttendanceEnabled(true);

        SubjectType existing = new SubjectType();
        existing.setId(1L);
        existing.setUuid(request.getUuid());
        existing.setGroup(true);
        existing.setAttendanceEnabled(true);
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(existing);
        AttendanceType existingType = completeAttendanceType();
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(existing)).thenReturn(List.of(existingType));

        service.saveSubjectType(request);

        verify(attendanceTypeRepository, never()).save(any(AttendanceType.class));
    }

    @Test
    public void allowsDisablingAttendanceWithoutValidatingConfig() {
        SubjectTypeContract request = baseRequest();
        request.setIsGroup(true);
        request.setAttendanceEnabled(false);

        SubjectType existing = new SubjectType();
        existing.setId(1L);
        existing.setUuid(request.getUuid());
        existing.setGroup(true);
        existing.setAttendanceEnabled(true);
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(existing);

        service.saveSubjectType(request);

        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void publicOverloadRejectsAttendanceOnIndividualType() {
        try {
            service.validateAttendanceEligibilityAndConfig(true, false, false, null);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("group"));
        }
    }

    @Test
    public void publicOverloadNoOpsWhenAttendanceDisabled() {
        service.validateAttendanceEligibilityAndConfig(false, false, false, null);
        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void validatorAcceptsRequestWithCompleteConfigEvenWhenDbConfigIsEmpty() {
        // Regression for the actual bug: validator must NOT query the DB; complete config in the request passes.
        AttendanceTypeContract contract = attendanceTypeContract("type-uuid", "Morning Prayer",
                AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT, "session-outcome-uuid",
                AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT, "absence-reason-uuid");

        service.validateAttendanceEligibilityAndConfig(true, true, false, List.of(contract));

        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void validatorRejectsRequestWithEmptyConfig() {
        AttendanceTypeContract contract = attendanceTypeContract("type-uuid", "Morning Prayer");
        try {
            service.validateAttendanceEligibilityAndConfig(true, true, false, List.of(contract));
            fail("Expected AttendanceConfigIncompleteException");
        } catch (AttendanceConfigIncompleteException e) {
            assertEquals(1, e.getIncompleteTypes().size());
            assertEquals(List.of(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT,
                            AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT),
                    e.getIncompleteTypes().get(0).getMissingFields());
        }
    }

    @Test
    public void validatorRejectsRequestMissingOneKey() {
        AttendanceTypeContract contract = attendanceTypeContract("type-uuid", "Morning Prayer",
                AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT, "session-outcome-uuid");
        try {
            service.validateAttendanceEligibilityAndConfig(true, true, false, List.of(contract));
            fail("Expected AttendanceConfigIncompleteException");
        } catch (AttendanceConfigIncompleteException e) {
            assertEquals(List.of(AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT),
                    e.getIncompleteTypes().get(0).getMissingFields());
        }
    }

    @Test
    public void validatorRejectsVoidedAttendanceTypesWithIncompleteConfig() {
        // Voided rows are still synced to clients; partial config would propagate and
        // cause inconsistent client behaviour, so we reject the save and force a reload.
        AttendanceTypeContract voided = attendanceTypeContract("type-uuid", "Old Type");
        voided.setVoided(true);

        try {
            service.validateAttendanceEligibilityAndConfig(true, true, false, List.of(voided));
            fail("Expected AttendanceConfigIncompleteException");
        } catch (AttendanceConfigIncompleteException e) {
            assertEquals(1, e.getIncompleteTypes().size());
        }
    }

    @Test
    public void validatorAllowsVoidedAttendanceTypesWithCompleteConfig() {
        AttendanceTypeContract voided = attendanceTypeContract("type-uuid", "Old Type",
                AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT, "session-outcome-uuid",
                AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT, "absence-reason-uuid");
        voided.setVoided(true);

        service.validateAttendanceEligibilityAndConfig(true, true, false, List.of(voided));

        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void validatorPassesWhenRequestHasNoAttendanceTypes() {
        service.validateAttendanceEligibilityAndConfig(true, true, false, null);
        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void seedHelperPubliclyCallableAndSeedsOnFlip() {
        SubjectType subjectType = new SubjectType();
        subjectType.setId(1L);
        subjectType.setUuid("subj-type-uuid");
        subjectType.setAttendanceEnabled(true);
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(subjectType)).thenReturn(Collections.emptyList());

        service.seedDefaultAttendanceTypeIfEnabling(subjectType, false);

        ArgumentCaptor<AttendanceType> captor = ArgumentCaptor.forClass(AttendanceType.class);
        verify(attendanceTypeRepository, times(1)).save(captor.capture());
        assertEquals("Attendance", captor.getValue().getName());
    }

    @Test
    public void seedHelperSkipsWhenAlreadyEnabled() {
        SubjectType subjectType = new SubjectType();
        subjectType.setId(1L);
        subjectType.setUuid("subj-type-uuid");
        subjectType.setAttendanceEnabled(true);

        service.seedDefaultAttendanceTypeIfEnabling(subjectType, true);

        verify(attendanceTypeRepository, never()).save(any(AttendanceType.class));
    }

    private SubjectTypeContract baseRequest() {
        SubjectTypeContract request = new SubjectTypeContract();
        request.setUuid("subj-type-uuid");
        request.setName("Class");
        request.setType("Group");
        return request;
    }

    private AttendanceType completeAttendanceType() {
        AttendanceType type = new AttendanceType();
        type.setUuid("complete-type");
        type.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT, "session-outcome-uuid")
                .with(AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT, "absence-reason-uuid"));
        return type;
    }

    private AttendanceTypeContract attendanceTypeContract(String uuid, String name, String... configEntries) {
        AttendanceTypeContract contract = new AttendanceTypeContract();
        contract.setUuid(uuid);
        contract.setName(name);
        Map<String, Object> config = new HashMap<>();
        for (int i = 0; i < configEntries.length; i += 2) {
            config.put(configEntries[i], configEntries[i + 1]);
        }
        contract.setConfig(config);
        return contract;
    }
}

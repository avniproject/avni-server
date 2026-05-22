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
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

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
    public void rejectsIncompleteAttendanceTypeWhenEnabled() {
        SubjectTypeContract request = baseRequest();
        request.setIsGroup(true);
        request.setAttendanceEnabled(true);

        SubjectType existing = new SubjectType();
        existing.setId(1L);
        existing.setUuid(request.getUuid());
        existing.setGroup(true);
        existing.setAttendanceEnabled(true);
        AttendanceType incomplete = new AttendanceType();
        incomplete.setUuid("type-uuid");
        incomplete.setName("Morning Prayer");
        incomplete.setConfig(new JsonObject().with(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT_UUID, "concept-uuid"));
        when(subjectTypeRepository.findByUuid(request.getUuid())).thenReturn(existing);
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(existing)).thenReturn(List.of(incomplete));

        try {
            service.saveSubjectType(request);
            fail("Expected AttendanceConfigIncompleteException");
        } catch (AttendanceConfigIncompleteException e) {
            assertEquals(1, e.getIncompleteTypes().size());
            assertEquals(List.of(AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT_UUID),
                    e.getIncompleteTypes().get(0).getMissingFields());
        }
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
            service.validateAttendanceEligibilityAndConfig(null, true, false, false);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("group"));
        }
    }

    @Test
    public void publicOverloadNoOpsWhenAttendanceDisabled() {
        service.validateAttendanceEligibilityAndConfig(null, false, false, false);
        verify(attendanceTypeRepository, never()).findBySubjectTypeAndIsVoidedFalse(any());
    }

    @Test
    public void publicOverloadValidatesExistingAttendanceTypes() {
        SubjectType existing = new SubjectType();
        existing.setId(1L);
        existing.setUuid("subj-type-uuid");
        existing.setGroup(true);
        AttendanceType incomplete = new AttendanceType();
        incomplete.setUuid("type-uuid");
        incomplete.setName("Morning Prayer");
        incomplete.setConfig(new JsonObject());
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(existing)).thenReturn(List.of(incomplete));

        try {
            service.validateAttendanceEligibilityAndConfig(existing, true, true, false);
            fail("Expected AttendanceConfigIncompleteException");
        } catch (AttendanceConfigIncompleteException e) {
            assertEquals(1, e.getIncompleteTypes().size());
            assertEquals(2, e.getIncompleteTypes().get(0).getMissingFields().size());
        }
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
                .with(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT_UUID, "session-outcome-uuid")
                .with(AttendanceTypeConfigKey.ABSENCE_REASON_CONCEPT_UUID, "absence-reason-uuid"));
        return type;
    }
}

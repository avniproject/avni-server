package org.avni.server.service.attendance;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.util.BadRequestError;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class AttendanceTypeServiceTest {

    @Mock
    private AttendanceTypeRepository attendanceTypeRepository;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private EncounterTypeRepository encounterTypeRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;

    private AttendanceTypeService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new AttendanceTypeService(attendanceTypeRepository, conceptRepository, encounterTypeRepository, subjectTypeRepository);
        when(attendanceTypeRepository.save(any(AttendanceType.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void rejectsUnknownConfigKey() {
        JsonObject config = new JsonObject().with("unknown_key", "value");

        try {
            service.validateConfig(config);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("unknown_key"));
        }
    }

    @Test
    public void acceptsEmptyConfig() {
        service.validateConfig(new JsonObject());
        service.validateConfig(null);
    }

    @Test
    public void rejectsVoidedConceptReference() {
        Concept voided = new Concept();
        voided.setUuid("concept-uuid");
        voided.setVoided(true);
        when(conceptRepository.findByUuid("concept-uuid")).thenReturn(voided);

        JsonObject config = new JsonObject()
                .with(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT_UUID, "concept-uuid");

        try {
            service.validateConfig(config);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("voided"));
        }
    }

    @Test
    public void rejectsMissingEncounterTypeReference() {
        when(encounterTypeRepository.findByUuid("et-uuid")).thenReturn(null);

        JsonObject config = new JsonObject()
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE_UUID, "et-uuid");

        try {
            service.validateConfig(config);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("et-uuid"));
        }
    }

    @Test
    public void blocksVoidingLastTypeWhenAttendanceEnabled() {
        SubjectType subjectType = new SubjectType();
        subjectType.setAttendanceEnabled(true);
        subjectType.setName("Class");

        AttendanceType type = new AttendanceType();
        type.setUuid("type-uuid");
        type.setSubjectType(subjectType);
        type.setVoided(true);
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(subjectType)).thenReturn(List.of(type));

        try {
            service.save(type);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("last"));
        }
    }

    @Test
    public void allowsVoidingWhenAnotherNonVoidedRemains() {
        SubjectType subjectType = new SubjectType();
        subjectType.setAttendanceEnabled(true);
        AttendanceType existing = new AttendanceType();
        existing.setUuid("existing-uuid");
        AttendanceType target = new AttendanceType();
        target.setUuid("target-uuid");
        target.setSubjectType(subjectType);
        target.setVoided(true);
        when(attendanceTypeRepository.findBySubjectTypeAndIsVoidedFalse(subjectType)).thenReturn(List.of(existing, target));

        service.save(target);
    }

    @Test
    public void surfacesDanglingWarningsForVoidedReferences() {
        Concept voided = new Concept();
        voided.setUuid("concept-uuid");
        voided.setVoided(true);
        when(conceptRepository.findByUuid("concept-uuid")).thenReturn(voided);
        when(encounterTypeRepository.findByUuid(any())).thenReturn(null);

        AttendanceType type = new AttendanceType();
        type.setUuid("type-uuid");
        type.setName("Morning Prayer");
        type.setConfig(new JsonObject()
                .with(AttendanceTypeConfigKey.SESSION_OUTCOME_REASON_CONCEPT_UUID, "concept-uuid")
                .with(AttendanceTypeConfigKey.FOLLOW_UP_ENCOUNTER_TYPE_UUID, "et-uuid"));

        List<DanglingRefWarning> warnings = service.surfaceDanglingReferences(type);

        assertEquals(2, warnings.size());
    }
}

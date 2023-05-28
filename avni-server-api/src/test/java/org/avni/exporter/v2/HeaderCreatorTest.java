package org.avni.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.application.Subject;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.exporter.v2.ExportFieldsManager;
import org.avni.server.exporter.v2.HeaderCreator;
import org.avni.server.service.FormMappingService;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.request.ExportEntityTypeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.*;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class HeaderCreatorTest {
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private EncounterTypeRepository encounterTypeRepository;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private ExportFieldsManager exportFieldsManager;
    @Mock
    private ProgramRepository programRepository;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void shouldAddOnlyPassedRegistrationHeaders() {
        SubjectType subjectType = getSubjectType("Individual", Subject.Person);

        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(exportFieldsManager.getCoreFields(any())).thenReturn(Arrays.asList(ID, UUID, FIRST_NAME, CREATED_BY, CREATED_DATE_TIME, LAST_MODIFIED_BY, LAST_MODIFIED_DATE_TIME));
        when(exportFieldsManager.getMainFields(any())).thenReturn(getStringFormElementMap());

        List<String> addressLevelTypes = Collections.singletonList("Village");
        HeaderCreator headerCreator = new HeaderCreator(subjectTypeRepository, formMappingService, addressLevelTypes, new HashMap<>(), encounterTypeRepository, exportFieldsManager, programRepository);

        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().build();

        headerCreator.visitSubject(exportEntityType);
        assertEquals("Individual_id,Individual_uuid,Individual_first_name,Individual_created_by,Individual_created_date_time,Individual_last_modified_by,Individual_last_modified_date_time,\"Village\",\"Individual_Question 1\"", headerCreator.getHeader());
    }

    @Test
    public void shouldAddQuestionGroupHeaders() {
        SubjectType subjectType = getSubjectType("Individual", Subject.Person);
        List<String> addressLevelTypes = Collections.singletonList("Village");
        HeaderCreator headerCreator = new HeaderCreator(subjectTypeRepository, formMappingService, addressLevelTypes, new HashMap<>(), encounterTypeRepository, exportFieldsManager, programRepository);

        Concept groupConcept = new ConceptBuilder().withName("GC").withDataType(ConceptDataType.QuestionGroup).withUuid("gc").build();
        Concept memberConcept1 = new ConceptBuilder().withName("MC1").withDataType(ConceptDataType.Text).withUuid("mc1").build();
        Concept memberConcept2 = new ConceptBuilder().withName("MC2").withDataType(ConceptDataType.Text).withUuid("mc2").build();
        FormElement groupFormElement = new TestFormElementBuilder().withConcept(groupConcept).build();
        FormElement formElement1 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept1).build();
        FormElement formElement2 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept2).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("gc", groupFormElement);
            put("c1", formElement1);
            put("c2", formElement2);
        }};

        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(exportFieldsManager.getCoreFields(any())).thenReturn(Collections.singletonList(ID));
        when(exportFieldsManager.getMainFields(any())).thenReturn(formElementsMap);

        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withFields(Collections.singletonList(ID)).build();
        headerCreator.visitSubject(exportEntityType);
        assertEquals("Individual_id,\"Village\",\"Individual_GC_MC1\",\"Individual_GC_MC2\"", headerCreator.getHeader());
    }

    @Test
    public void shouldAddRepeatableQuestionGroupHeaders() {
        SubjectType subjectType = getSubjectType("Individual", Subject.Person);
        List<String> addressLevelTypes = Collections.singletonList("Village");


        Concept groupConcept = new ConceptBuilder().withName("GC").withDataType(ConceptDataType.QuestionGroup).withUuid("gc").build();
        Concept memberConcept1 = new ConceptBuilder().withName("MC1").withDataType(ConceptDataType.Text).withUuid("mc1").build();
        Concept memberConcept2 = new ConceptBuilder().withName("MC2").withDataType(ConceptDataType.Text).withUuid("mc2").build();
        FormElement groupFormElement = new TestFormElementBuilder().withRepeatable(true).withConcept(groupConcept).build();
        FormElement formElement1 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept1).build();
        FormElement formElement2 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept2).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("gc", groupFormElement);
            put("c1", formElement1);
            put("c2", formElement2);
        }};

        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(exportFieldsManager.getCoreFields(any())).thenReturn(Collections.singletonList(ID));
        when(exportFieldsManager.getMainFields(any())).thenReturn(formElementsMap);

        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withFields(Collections.singletonList(ID)).build();
        HashMap<FormElement, Integer> maxRepeatableQuestionGroupObservation = new HashMap<FormElement, Integer>() {{
            put(groupFormElement, 2);
        }};
        HeaderCreator headerCreator = new HeaderCreator(subjectTypeRepository, formMappingService, addressLevelTypes, maxRepeatableQuestionGroupObservation, encounterTypeRepository, exportFieldsManager, programRepository);
        headerCreator.visitSubject(exportEntityType);
        assertEquals("Individual_id,\"Village\",\"Individual_GC_1_MC1\",\"Individual_GC_1_MC2\",\"Individual_GC_2_MC1\",\"Individual_GC_2_MC2\"", headerCreator.getHeader());
    }

    @Test
    public void shouldGenerateMultipleColumnsForEncounter() {
        List<String> addressLevelTypes = Collections.singletonList("Village");
        HeaderCreator headerCreator = new HeaderCreator(subjectTypeRepository, formMappingService, addressLevelTypes, new HashMap<>(), encounterTypeRepository, exportFieldsManager, programRepository);

        when(exportFieldsManager.getCoreFields(any())).thenReturn(Arrays.asList(ID, CREATED_BY, CREATED_DATE_TIME, LAST_MODIFIED_BY, LAST_MODIFIED_DATE_TIME));
        when(exportFieldsManager.getMaxEntityCount(any())).thenReturn(2l);
        when(exportFieldsManager.getMainFields(any())).thenReturn(getStringFormElementMap());
        when(encounterTypeRepository.findByUuid(any())).thenReturn(new EncounterTypeBuilder().withName("ENC").build());

        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().build();
        headerCreator.visitEncounter(exportEntityType, new ExportEntityType());
        assertEquals(",ENC_1_id,ENC_1_created_by,ENC_1_created_date_time,ENC_1_last_modified_by,ENC_1_last_modified_date_time,\"ENC_1_Question 1\",ENC_2_id,ENC_2_created_by,ENC_2_created_date_time,ENC_2_last_modified_by,ENC_2_last_modified_date_time,\"ENC_2_Question 1\"", headerCreator.getHeader());
    }

    private SubjectType getSubjectType(String name, Subject type) {
        SubjectType subjectType = new SubjectType();
        subjectType.setName(name);
        subjectType.setUuid("1890a382-8f47-4d2c-9126-40cb38a22e1f");
        subjectType.setId(200L);
        subjectType.setType(type);
        return subjectType;
    }

    private Map<String, FormElement> getStringFormElementMap() {
        Map<String, FormElement> map = new HashMap<>();
        Concept concept = new Concept();
        FormElement formElement = new FormElement();
        formElement.setType("SingleSelect");
        formElement.setConcept(concept);
        concept.setDataType(ConceptDataType.Text.toString());
        concept.setName("Question 1");
        map.put("Question 1 uuid", formElement);
        return map;
    }
}

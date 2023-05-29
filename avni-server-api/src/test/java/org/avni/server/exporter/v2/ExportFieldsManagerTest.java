package org.avni.server.exporter.v2;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormType;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.service.FormMappingService;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.request.ExportEntityTypeBuilder;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.*;

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ExportFieldsManagerTest {
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private ProgramEncounterRepository programEncounterRepository;
    private ExportFieldsManager exportFieldsManager;

    @Before
    public void setup() {
        initMocks(this);
        exportFieldsManager = new ExportFieldsManager(formMappingService, encounterRepository, programEncounterRepository, TimeZone.getDefault().getDisplayName());
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoRegistrationFieldsArePassed() {
        ExportEntityType exportEntityType = givenNoStaticHeaders(FormType.IndividualProfile);
        exportFieldsManager.visitSubject(exportEntityType);
        verifyStaticHeader(exportEntityType, FIRST_NAME);
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoEncounterFieldsArePassed() {
        ExportEntityType exportEntityType = givenNoStaticHeaders(FormType.Encounter);
        exportFieldsManager.visitEncounter(exportEntityType, new ExportEntityTypeBuilder().withUuid("st1").build());
        verifyStaticHeader(exportEntityType, ENCOUNTER_DATE_TIME);
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoProgramEnrolmentFieldsArePassed() {
        ExportEntityType exportEntityType = givenNoStaticHeaders(FormType.ProgramEnrolment);
        exportFieldsManager.visitProgram(exportEntityType, new ExportEntityTypeBuilder().withUuid("st1").build());
        verifyStaticHeader(exportEntityType, ENROLMENT_DATE_TIME);
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoProgramEncounterFieldsArePassed() {
        ExportEntityType exportEntityType = givenNoStaticHeaders(FormType.ProgramEncounter);
        exportFieldsManager.visitProgramEncounter(exportEntityType, new ExportEntityTypeBuilder().withUuid("p1").build(), new ExportEntityTypeBuilder().withUuid("st1").build());
        verifyStaticHeader(exportEntityType, ENCOUNTER_DATE_TIME);
    }

    private void verifyStaticHeader(ExportEntityType exportEntityType, String fieldName) {
        List<String> coreFields = exportFieldsManager.getCoreFields(exportEntityType);
        assertTrue(coreFields.contains(fieldName));
    }

    private ExportEntityType givenNoStaticHeaders(FormType formType) {
        when(formMappingService.getAllFormElementsAndDecisionMap(any(), eq(null), eq(null), eq(formType))).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.findForEncounter(any(), any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.findForProgram(any(), any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        return new ExportEntityTypeBuilder().withUuid("a").build();
    }

    @Test
    public void shouldAddQuestionGroupFormElementForSubject() {
        LinkedHashMap<String, FormElement> formElements = givenQuestionGroupFormElement();
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUuid("st1").withFields(Collections.singletonList(UUID)).build();
        when(formMappingService.getAllFormElementsAndDecisionMap(eq(exportEntityType.getUuid()), eq(null), eq(null), eq(FormType.IndividualProfile))).thenReturn(formElements);
        exportFieldsManager.visitSubject(exportEntityType);
        verifyMainFields(exportEntityType);
    }

    @Test
    public void shouldAddQuestionGroupFormElementForEncounter() {
        LinkedHashMap<String, FormElement> formElements = givenQuestionGroupFormElement();
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUuid("et1").withFields(Collections.singletonList(UUID)).build();
        ExportEntityType subjectExportEntityType = new ExportEntityTypeBuilder().withUuid("st1").build();
        when(formMappingService.getAllFormElementsAndDecisionMap(eq(subjectExportEntityType.getUuid()), eq(null), eq(exportEntityType.getUuid()), eq(FormType.Encounter))).thenReturn(formElements);
        exportFieldsManager.visitEncounter(exportEntityType, subjectExportEntityType);
        verifyMainFields(exportEntityType);
    }

    private void verifyMainFields(ExportEntityType exportEntityType) {
        Map<String, FormElement> mainFields = exportFieldsManager.getMainFields(exportEntityType);
        assertEquals(2, mainFields.size());
    }

    private LinkedHashMap<String, FormElement> givenQuestionGroupFormElement() {
        Concept c1 = new ConceptBuilder().withUuid("c1").withDataType(ConceptDataType.Text).build();
        Concept c2 = new ConceptBuilder().withUuid("c2").withDataType(ConceptDataType.QuestionGroup).build();
        FormElement groupFormElement = new TestFormElementBuilder().withConcept(c2).build();

        LinkedHashMap<String, FormElement> formElements = new LinkedHashMap<String, FormElement>() {{
            put("f1", new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(c1).build());
            put("f2", groupFormElement);
        }};

        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.findForEncounter(any(), any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.findForProgram(any(), any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        return formElements;
    }
}

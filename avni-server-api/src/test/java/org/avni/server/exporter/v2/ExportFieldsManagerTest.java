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

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.FIRST_NAME;
import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.UUID;
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
    public void shouldAddAllStaticHeadersIfNoFieldsIsPassed() {
        when(formMappingService.getAllFormElementsAndDecisionMap(any(), eq(null), eq(null), eq(FormType.IndividualProfile))).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUuid("a").build();
        exportFieldsManager.visitSubject(exportEntityType);
        List<String> coreFields = exportFieldsManager.getCoreFields(exportEntityType);
        assertTrue(coreFields.contains(FIRST_NAME));
    }

    @Test
    public void shouldAddQuestionGroupFormElement() {
        Concept c1 = new ConceptBuilder().withUuid("c1").withDataType(ConceptDataType.Text).build();
        Concept c2 = new ConceptBuilder().withUuid("c2").withDataType(ConceptDataType.QuestionGroup).build();
        FormElement groupFormElement = new TestFormElementBuilder().withConcept(c2).build();

        LinkedHashMap<String, FormElement> formElements = new LinkedHashMap<String, FormElement>() {{
            put("f1", new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(c1).build());
            put("f2", groupFormElement);
        }};

        when(formMappingService.getAllFormElementsAndDecisionMap(any(), eq(null), eq(null), eq(FormType.IndividualProfile))).thenReturn(formElements);
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());

        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUuid("st1").withFields(Collections.singletonList(UUID)).build();
        exportFieldsManager.visitSubject(exportEntityType);
        Map<String, FormElement> mainFields = exportFieldsManager.getMainFields(exportEntityType);
        assertEquals(2, mainFields.size());
    }
}

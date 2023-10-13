package org.avni.server.service;

import com.google.common.collect.ImmutableMap;
import org.avni.server.application.*;
import org.avni.server.builder.FormBuilder;
import org.avni.server.builder.FormBuilderException;
import org.avni.server.common.ValidationResult;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.util.BugsnagReporter;
import org.avni.server.web.request.ObservationRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.Decision;
import org.avni.server.web.validation.ValidationException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EnhancedValidationServiceQuestionGroupsTest {
    @Mock
    private FormMappingService formMappingService;
    @Mock
    private OrganisationConfigService organisationConfigService;
    @Mock
    private BugsnagReporter bugsnagReporter;
    @Mock
    private ConceptRepository conceptRepository;
    @Mock
    private SubjectTypeRepository subjectTypeRepository;
    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private AddressLevelTypeRepository addressLevelTypeRepository;
    @Mock
    private S3Service s3Service;

    private EnhancedValidationService enhancedValidationService;
    private List<ObservationRequest> observationRequests;
    private List<Decision> decisions;
    private Form form;
    private FormMapping formMapping;
    private SubjectType subjectType;
    private LinkedHashMap<String, FormElement> entityConceptMap;
    private LinkedHashMap<String, FormElement> entityConceptMapForQG1;
    private LinkedHashMap<String, FormElement> entityConceptMapForQG2;

    private Concept groupConcept1;
    private Concept groupConcept1Concept1;
    private Concept groupConcept2;
    private Concept groupConcept2Concept1;
    private FormElement groupFormElement1;
    private FormElement formElement1_1;
    private FormElement groupFormElement2;
    private FormElement formElement2_1;
    private FormElementGroup formElementGroup;
    private Form questionGroupForm;


    @Before
    public void setup() throws FormBuilderException {
        initMocks(this);
        enhancedValidationService = new EnhancedValidationService(formMappingService, organisationConfigService, bugsnagReporter, conceptRepository, subjectTypeRepository, individualRepository, addressLevelTypeRepository, s3Service);
        observationRequests = new ArrayList<>();
        decisions = new ArrayList<>();
        subjectType = new SubjectTypeBuilder().setUuid("f6cfd71c-1430-44eb-8579-f66a98e1d57f").setName("Individual").build();
        form = new FormBuilder(null).withType(FormType.IndividualProfile.name()).withUUID("150cb660-ebdb-4386-b6ea-6398fe7b63dd").withName("Form").build();
        formMapping = new FormMappingBuilder().withForm(form).withSubjectType(subjectType).withUuid("f6cfd71c-1324-44eb-8579-f66a98e1d57f").build();

        entityConceptMap = new LinkedHashMap<>();
        entityConceptMapForQG1 = new LinkedHashMap<>();
        entityConceptMapForQG2 = new LinkedHashMap<>();

        groupConcept1 = new ConceptBuilder().withName("GC1").withUuid("gc1").withId(1).withDataType(ConceptDataType.QuestionGroup).build();
        groupConcept1Concept1 = new ConceptBuilder().withName("GC1-C1").withId(2).withUuid("gc1-c1").withDataType(ConceptDataType.Text).build();
        groupConcept2 = new ConceptBuilder().withName("GC2").withId(3).withDataType(ConceptDataType.QuestionGroup).withUuid("gc2").build();
        groupConcept2Concept1 = new ConceptBuilder().withId(4).withName("GC2-C1").withUuid("gc2-c1").withDataType(ConceptDataType.Text).build();
        groupFormElement1 = new TestFormElementBuilder().withUuid("groupConcept1").withConcept(groupConcept1).withId(1).withRepeatable(true).build();
        formElement1_1 = new TestFormElementBuilder().withUuid("groupConcept1Concept1").withQuestionGroupElement(groupFormElement1).withId(2).withConcept(groupConcept1Concept1).build();
        groupFormElement2 = new TestFormElementBuilder().withUuid("groupConcept2").withId(3).withConcept(groupConcept2).withRepeatable(false).build();
        formElement2_1 = new TestFormElementBuilder().withUuid("groupConcept2Concept1").withId(4).withQuestionGroupElement(groupFormElement2).withConcept(groupConcept2Concept1).build();
        formElementGroup = new TestFormElementGroupBuilder().addFormElement(groupFormElement1, formElement1_1, groupFormElement2, formElement2_1).build();
        questionGroupForm = new TestFormBuilder().addFormElementGroup(formElementGroup).build();

        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());

        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(questionGroupForm).build());
        when(formMappingService.getEntityConceptMap(any(), eq(true))).thenReturn(entityConceptMap);
        when(formMappingService.getEntityConceptMapForSpecificQuestionGroupFormElement(eq(groupFormElement1), any(), eq(true))).thenReturn(entityConceptMapForQG1);
        when(formMappingService.getEntityConceptMapForSpecificQuestionGroupFormElement(eq(groupFormElement2), any(), eq(true))).thenReturn(entityConceptMapForQG2);

        when(conceptRepository.findByName(groupConcept1.getName())).thenReturn(groupConcept1);
        when(conceptRepository.findByName(groupConcept1Concept1.getName())).thenReturn(groupConcept1Concept1);
        when(conceptRepository.findByName(groupConcept2.getName())).thenReturn(groupConcept2);
        when(conceptRepository.findByName(groupConcept2Concept1.getName())).thenReturn(groupConcept2Concept1);

        when(conceptRepository.findByUuid(groupConcept1.getUuid())).thenReturn(groupConcept1);
        when(conceptRepository.findByUuid(groupConcept1Concept1.getUuid())).thenReturn(groupConcept1Concept1);
        when(conceptRepository.findByUuid(groupConcept2.getUuid())).thenReturn(groupConcept2);
        when(conceptRepository.findByUuid(groupConcept2Concept1.getUuid())).thenReturn(groupConcept2Concept1);

        entityConceptMap.put(groupConcept1.getUuid(), groupFormElement1);
        entityConceptMap.put(groupConcept1Concept1.getUuid(), formElement1_1);
        entityConceptMap.put(groupConcept2.getUuid(), groupFormElement2);
        entityConceptMap.put(groupConcept2Concept1.getUuid(), formElement2_1);

        entityConceptMapForQG1.put(groupConcept1Concept1.getUuid(), formElement1_1);
        entityConceptMapForQG2.put(groupConcept2Concept1.getUuid(), formElement2_1);
    }


    @Test
    public void shouldReturnValidationSuccessForValidQuestionGroupObservations() {
        ObservationRequest observationRequestRepeatableQG = new ObservationRequest();
        observationRequestRepeatableQG.setConceptUUID(groupConcept1.getUuid());
        observationRequestRepeatableQG.setConceptName(groupConcept1.getName());
        List<ImmutableMap<String, String>> immutableMaps = Arrays.asList(ImmutableMap.of(groupConcept1Concept1.getUuid(), "abc123"),
                ImmutableMap.of(groupConcept1Concept1.getUuid(), "def456"));
        observationRequestRepeatableQG.setValue(immutableMaps);

        ObservationRequest observationRequestNonRepeatableQG = new ObservationRequest();
        observationRequestNonRepeatableQG.setConceptUUID(groupConcept2.getUuid());
        observationRequestNonRepeatableQG.setConceptName(groupConcept2.getName());
        observationRequestNonRepeatableQG.setValue(ImmutableMap.of(groupConcept2Concept1.getUuid(), "ghi789"));

        observationRequests = Arrays.asList(observationRequestRepeatableQG, observationRequestNonRepeatableQG);

        ValidationResult validationResult = enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
        assertTrue(validationResult.isSuccess());
    }

    @Test(expected = ValidationException.class)
    public void shouldReturnValidationFailureForInValidConceptsForFormWithinQuestionGroupConcept() {
        ObservationRequest observationRequestRepeatableQG = new ObservationRequest();
        observationRequestRepeatableQG.setConceptUUID(groupConcept1.getUuid());
        observationRequestRepeatableQG.setConceptName(groupConcept1.getName());
        List<ImmutableMap<String, String>> immutableMaps = Arrays.asList(ImmutableMap.of("invalid-uuid1", "abc123"),
                ImmutableMap.of(groupConcept1Concept1.getUuid(), "def456"));
        observationRequestRepeatableQG.setValue(immutableMaps);


        observationRequests = Arrays.asList(observationRequestRepeatableQG);

        ValidationResult validationResult = enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
        assertTrue(validationResult.isSuccess());
    }

    @Test(expected = ValidationException.class)
    public void shouldReturnValidationFailureForValidConceptsForFormButInvalidWithinQuestionGroupConcept() {
        ObservationRequest observationRequestRepeatableQG = new ObservationRequest();
        observationRequestRepeatableQG.setConceptUUID(groupConcept1.getUuid());
        observationRequestRepeatableQG.setConceptName(groupConcept1.getName());
        List<ImmutableMap<String, String>> immutableMaps = Arrays.asList(ImmutableMap.of(groupConcept2Concept1.getUuid(), "abc123"),
                ImmutableMap.of(groupConcept1Concept1.getUuid(), "def456"));
        observationRequestRepeatableQG.setValue(immutableMaps);

        observationRequests = Arrays.asList(observationRequestRepeatableQG);

        ValidationResult validationResult = enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
        assertTrue(validationResult.isSuccess());
    }

}

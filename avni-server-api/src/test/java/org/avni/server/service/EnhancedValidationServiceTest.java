package org.avni.server.service;

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

public class EnhancedValidationServiceTest {
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
    private Concept firstNonCodedConcept;
    private Concept firstDecisionConcept;
    private FormElement firstNonCodedConceptFormElement;
    private FormElement firstDecisionConceptFormElement;


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
        firstNonCodedConcept = new ConceptBuilder().withDataType(ConceptDataType.Text).withName("firstNonCodedConcept").withUuid("f87d7ffe-1b89-447a-a10e-594a231f50c2").build();
        firstDecisionConcept = new ConceptBuilder().withDataType(ConceptDataType.Text).withName("firstDecisionConcept").withUuid("0d294d33-1695-4965-8798-cc97e0407994").build();
        firstNonCodedConceptFormElement = new TestFormElementBuilder().withUuid("fe-uuid-1").withConcept(firstNonCodedConcept).build();
        firstDecisionConceptFormElement = new TestFormElementBuilder().withUuid("fe-uuid-2").withConcept(firstDecisionConcept).build();

    }



    @Test(expected = ValidationException.class)
    public void shouldThrowValidationExceptionForInvalidDataIfFailOnValidationIsEnabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        String errorMessage = "Dummy Error Message";
        enhancedValidationService.handleValidationFailure(errorMessage);
    }

    @Test
    public void shouldReturnValidationFailureForInvalidDataIfFailOnValidationIsDisabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(false);
        String errorMessage = "Dummy Error Message";
        ValidationResult validationResult = enhancedValidationService.handleValidationFailure(errorMessage);
        assertTrue(validationResult.isFailure());
    }

    @Test
    public void shouldReturnValidationSuccessForEmptyDataIfFailOnValidationIsEnabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        when(formMappingService.getEntityConceptMap(any(), eq(true))).thenReturn(entityConceptMap);

        ValidationResult validationResult = enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
        assertTrue(validationResult.isSuccess());
    }

    @Test(expected = ValidationException.class)
    public void shouldReturnValidationFailureForInValidDataIfFailOnValidationIsEnabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getEntityConceptMap(any(), eq(true))).thenReturn(entityConceptMap);
        when(conceptRepository.findByName(firstNonCodedConcept.getName())).thenReturn(firstNonCodedConcept);
        when(conceptRepository.findByName(firstDecisionConcept.getName())).thenReturn(firstDecisionConcept);

        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(firstNonCodedConcept.getUuid());
        observationRequest.setConceptName(firstNonCodedConcept.getName());
        observationRequest.setValue("Dummy");
        observationRequests.add(observationRequest);
        Decision decision = new Decision();
        decision.setName(firstDecisionConcept.getName());
        decision.setValue("DummyDecision");
        decisions.add(decision);
        entityConceptMap = new LinkedHashMap<>();
        enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
    }

    @Test
    public void shouldReturnValidationSuccessForValidSingleNonCodedConceptAndDecisionIfFailOnValidationIsEnabled() {
        when(organisationConfigService.isFailOnValidationErrorEnabled()).thenReturn(true);
        when(subjectTypeRepository.findByUuid(any())).thenReturn(subjectType);
        when(formMappingService.getAllFormElementsAndDecisionMap("st1", null, null, FormType.IndividualProfile)).thenReturn(new LinkedHashMap<>());
        when(formMappingService.findForSubject(any())).thenReturn(new FormMappingBuilder().withForm(new Form()).build());
        when(formMappingService.getEntityConceptMap(any(), eq(true))).thenReturn(entityConceptMap);
        when(conceptRepository.findByName(firstNonCodedConcept.getName())).thenReturn(firstNonCodedConcept);
        when(conceptRepository.findByName(firstDecisionConcept.getName())).thenReturn(firstDecisionConcept);
        when(conceptRepository.findByUuid(firstNonCodedConcept.getUuid())).thenReturn(firstNonCodedConcept);
        when(conceptRepository.findByUuid(firstDecisionConcept.getUuid())).thenReturn(firstDecisionConcept);

        entityConceptMap.put(firstNonCodedConcept.getUuid(), firstNonCodedConceptFormElement);
        entityConceptMap.put(firstDecisionConcept.getUuid(), firstDecisionConceptFormElement);

        ObservationRequest observationRequest = new ObservationRequest();
        observationRequest.setConceptUUID(firstNonCodedConcept.getUuid());
        observationRequest.setConceptName(firstNonCodedConcept.getName());
        observationRequest.setValue("Dummy");
        observationRequests = Arrays.asList(observationRequest);
        Decision decision = new Decision();
        decision.setName(firstDecisionConcept.getName());
        decision.setValue("DummyDecision");
        decisions = Arrays.asList(decision);

        ValidationResult validationResult = enhancedValidationService.validateObservationsAndDecisionsAgainstFormMapping(observationRequests, decisions, formMapping);
        assertTrue(validationResult.isSuccess());
    }

}

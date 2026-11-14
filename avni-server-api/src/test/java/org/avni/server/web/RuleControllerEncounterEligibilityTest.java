package org.avni.server.web;

import org.avni.server.application.FormMapping;
import org.avni.server.application.Subject;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.EncounterTypeBuilder;
import org.avni.server.domain.Individual;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.IndividualService;
import org.avni.server.service.RuleFailureTelemetryService;
import org.avni.server.service.RuleService;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.avni.server.web.request.rules.response.RuleError;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.ResponseEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RuleControllerEncounterEligibilityTest {
    private final RuleService ruleService = mock(RuleService.class);
    private final IndividualRepository individualRepository = mock(IndividualRepository.class);
    private final FormMappingRepository formMappingRepository = mock(FormMappingRepository.class);
    private final IndividualService individualService = mock(IndividualService.class);
    private final RuleFailureTelemetryService ruleFailureTelemetryService = mock(RuleFailureTelemetryService.class);
    private final RuleController ruleController = new RuleController(ruleService, mock(ProgramEnrolmentRepository.class), individualRepository, formMappingRepository, individualService, null, null, ruleFailureTelemetryService);

    private final SubjectType subjectType = new SubjectTypeBuilder().setUuid("person").setType(Subject.Person).build();
    private final Individual subject = new SubjectBuilder().withUUID("subject").withSubjectType(subjectType).build();
    private final EncounterType fineVisit = new EncounterTypeBuilder().withUuid("fine-visit").build();
    private final EncounterType throwingVisit = new EncounterTypeBuilder().withUuid("throwing-visit").build();
    private final EncounterType ineligibleVisit = new EncounterTypeBuilder().withUuid("ineligible-visit").build();
    private final RuleError error = new RuleError();

    @Before
    public void setUp() {
        error.setMessage("QA1079 encounter eligibility rule throws");
        when(individualRepository.findByUuid("subject")).thenReturn(subject);
        List<FormMapping> formMappings = Arrays.asList(fineVisit, throwingVisit, ineligibleVisit).stream()
                .map(encounterType -> new FormMappingBuilder().withSubjectType(subjectType).withEncounterType(encounterType).build())
                .collect(Collectors.toList());
        when(formMappingRepository.getAllGeneralEncounterFormMappings()).thenReturn(formMappings);
        when(individualService.constructEncounters(any())).thenReturn(Collections.emptySet());
    }

    @Test
    public void anEncounterTypeWhoseRuleThrowsIsEligibleAndItsFailureIsRecorded() throws RuleExecutionException {
        whenRuleServerReturns(throwingResult(false));

        assertEquals(Arrays.asList("fine-visit", "throwing-visit"), eligibleEncounterTypeUuids());
        verify(ruleFailureTelemetryService).recordEligibilityRuleFailure(subject, "throwing-visit", SourceType.EncounterEligibilityCheck, error);
        verify(ruleFailureTelemetryService, times(1)).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void nothingIsRecordedWhenNoRuleFails() throws RuleExecutionException {
        when(ruleService.executeEligibilityRule(any(), any())).thenReturn(success(Arrays.asList(
                new EligibilityRuleEntity(true, "fine-visit"), new EligibilityRuleEntity(false, "ineligible-visit"))));

        assertEquals(Collections.singletonList("fine-visit"), eligibleEncounterTypeUuids());
        verify(ruleFailureTelemetryService, never()).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void ifTheRuleFailureCannotBeRecorded_theEncounterTypesAreStillReturned() throws RuleExecutionException {
        whenRuleServerReturns(throwingResult(true));
        doThrow(new RuntimeException("could not save")).when(ruleFailureTelemetryService).recordEligibilityRuleFailure(any(), any(), any(), any());

        assertEquals(Arrays.asList("fine-visit", "throwing-visit"), eligibleEncounterTypeUuids());
    }

    @Test
    public void whenTheRuleServerFailsTheWholeCheck_noEncounterTypeIsEligibleAndNothingIsRecorded() throws RuleExecutionException {
        EligibilityRuleResponseEntity failure = new EligibilityRuleResponseEntity();
        failure.setStatus("failure");
        failure.setError(error);
        when(ruleService.executeEligibilityRule(any(), any())).thenReturn(failure);

        assertEquals(Collections.emptyList(), eligibleEncounterTypeUuids());
        verify(ruleFailureTelemetryService, never()).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void anUnknownSubjectIsNotFound() {
        assertEquals(404, ruleController.getEligibleGeneralEncounters("unknown-subject").getStatusCode().value());
    }

    @Test
    public void anUnknownEnrolmentIsNotFound() {
        assertEquals(404, ruleController.getEligibleProgramEncounters("unknown-enrolment").getStatusCode().value());
    }

    private EligibilityRuleEntity throwingResult(boolean isEligible) {
        EligibilityRuleEntity throwing = new EligibilityRuleEntity(isEligible, "throwing-visit");
        throwing.setError(error);
        return throwing;
    }

    private void whenRuleServerReturns(EligibilityRuleEntity throwing) throws RuleExecutionException {
        when(ruleService.executeEligibilityRule(any(), any())).thenReturn(success(Arrays.asList(
                new EligibilityRuleEntity(true, "fine-visit"), throwing, new EligibilityRuleEntity(false, "ineligible-visit"))));
    }

    private static EligibilityRuleResponseEntity success(List<EligibilityRuleEntity> entities) {
        EligibilityRuleResponseEntity response = new EligibilityRuleResponseEntity(entities);
        response.setStatus("success");
        return response;
    }

    @SuppressWarnings("unchecked")
    private List<String> eligibleEncounterTypeUuids() {
        ResponseEntity<?> response = ruleController.getEligibleGeneralEncounters("subject");
        assertEquals(200, response.getStatusCode().value());
        return (List<String>) ((JsonObject) response.getBody()).get("eligibleEncounterTypeUUIDs");
    }
}

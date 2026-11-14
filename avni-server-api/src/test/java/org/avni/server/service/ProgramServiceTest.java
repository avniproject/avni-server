package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.avni.server.web.request.rules.response.RuleError;
import org.junit.Test;

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

public class ProgramServiceTest {
    @Test
    public void multipleEnrolmentsProgramsAreStaticallyAlwaysEligible() throws RuleExecutionException {
        Program program = new ProgramBuilder().withUuid("1").allowMultipleEnrolments(true).build();
        List<Program> eligiblePrograms = getEligiblePrograms(program);
        assertEquals(1, eligiblePrograms.size());
    }

    @Test
    public void nonMultipleEnrolmentsProgramsAreStaticallyNotEligible() throws RuleExecutionException {
        Program program = new ProgramBuilder().withUuid("1").allowMultipleEnrolments(false).build();
        List<Program> eligiblePrograms = getEligiblePrograms(program);
        assertEquals(0, eligiblePrograms.size());
    }

    public List<Program> getEligiblePrograms(Program program) throws RuleExecutionException {
        ProgramEnrolment enrolment = new ProgramEnrolmentBuilder().setProgram(program).build();
        Individual subject = new SubjectBuilder().addEnrolment(enrolment).build();

        FormMapping formMapping = new FormMappingBuilder().withProgram(program).build();
        List<FormMapping> formMappings = Collections.singletonList(formMapping);

        FormMappingRepository formMappingRepository = mock(FormMappingRepository.class);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(any(), any())).thenReturn(formMappings);
        RuleService ruleService = mock(RuleService.class);

        EligibilityRuleResponseEntity ruleResponseEntity = new EligibilityRuleResponseEntity(Collections.singletonList(new EligibilityRuleEntity(true, program.getUuid())));
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(ruleResponseEntity);
        ProgramService programService = new ProgramService(null, null, formMappingRepository, ruleService, new EligibilityCheckService(mock(RuleFailureTelemetryService.class)));
        return programService.getEligiblePrograms(subject);
    }

    private final Program fineProgram = new ProgramBuilder().withUuid("fine").build();
    private final Program throwingProgram = new ProgramBuilder().withUuid("throwing").build();
    private final Program ineligibleProgram = new ProgramBuilder().withUuid("ineligible").build();
    private final Individual subjectWithoutEnrolments = new SubjectBuilder().build();
    private final RuleService ruleService = mock(RuleService.class);
    private final RuleFailureTelemetryService ruleFailureTelemetryService = mock(RuleFailureTelemetryService.class);

    @Test
    public void aProgramWhoseRuleThrowsIsEligibleAndItsFailureIsRecorded_otherProgramsKeepTheirOwnResult() throws RuleExecutionException {
        EligibilityRuleEntity throwingResult = new EligibilityRuleEntity(false, "throwing");
        throwingResult.setError(ruleError("QA1079 eligibility rule throws"));
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(new EligibilityRuleResponseEntity(Arrays.asList(
                new EligibilityRuleEntity(true, "fine"), throwingResult, new EligibilityRuleEntity(false, "ineligible"))));

        List<Program> eligiblePrograms = eligibleProgramsFor(fineProgram, throwingProgram, ineligibleProgram);

        assertEquals(Arrays.asList(fineProgram, throwingProgram), eligiblePrograms);
        verify(ruleFailureTelemetryService).recordEligibilityRuleFailure(subjectWithoutEnrolments, "throwing", SourceType.EnrolmentEligibilityCheck, throwingResult.getError());
        verify(ruleFailureTelemetryService, times(1)).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void whenTheRuleServerFailsTheWholeCheck_noProgramIsEligibleAndNothingIsRecorded() throws RuleExecutionException {
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(failedResponse(ruleError("Request failed with status code 401")));

        assertEquals(Collections.emptyList(), eligibleProgramsFor(fineProgram, throwingProgram, ineligibleProgram));
        verify(ruleService, times(1)).executeProgramEligibilityCheckRule(any(), any());
        verify(ruleFailureTelemetryService, never()).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void whenTheRuleServerFailsTheCheckForASingleProgram_itIsNotEligible() throws RuleExecutionException {
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(failedResponse(ruleError("Request failed with status code 401")));

        assertEquals(Collections.emptyList(), eligibleProgramsFor(fineProgram));
        verify(ruleFailureTelemetryService, never()).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void whenTheRuleServerCannotBeReached_noProgramIsEligibleAndNothingIsRecorded() throws RuleExecutionException {
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenThrow(new RuleExecutionException(ruleError("Connection refused")));

        assertEquals(Collections.emptyList(), eligibleProgramsFor(fineProgram, throwingProgram));
        verify(ruleService, times(1)).executeProgramEligibilityCheckRule(any(), any());
        verify(ruleFailureTelemetryService, never()).recordEligibilityRuleFailure(any(), any(), any(), any());
    }

    @Test
    public void ifTheRuleFailureCannotBeRecorded_theProgramsAreStillReturned() throws RuleExecutionException {
        EligibilityRuleEntity throwingResult = new EligibilityRuleEntity(true, "throwing");
        throwingResult.setError(ruleError("QA1079 eligibility rule throws"));
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(new EligibilityRuleResponseEntity(Arrays.asList(
                new EligibilityRuleEntity(true, "fine"), throwingResult)));
        doThrow(new RuntimeException("could not save")).when(ruleFailureTelemetryService).recordEligibilityRuleFailure(any(), any(), any(), any());

        assertEquals(Arrays.asList(fineProgram, throwingProgram), eligibleProgramsFor(fineProgram, throwingProgram));
    }

    @Test
    public void whenNoProgramIsLeftToCheck_theRuleServerIsNotCalled() throws RuleExecutionException {
        assertEquals(Collections.emptyList(), eligibleProgramsFor());
        verify(ruleService, never()).executeProgramEligibilityCheckRule(any(), any());
    }

    @Test
    public void aProgramMappedTwiceIsCheckedAndListedOnce() throws RuleExecutionException {
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenReturn(new EligibilityRuleResponseEntity(Collections.singletonList(new EligibilityRuleEntity(true, "fine"))));

        assertEquals(Collections.singletonList(fineProgram), eligibleProgramsFor(fineProgram, fineProgram));
        verify(ruleService).executeProgramEligibilityCheckRule(subjectWithoutEnrolments, Collections.singletonList(fineProgram));
    }

    private List<Program> eligibleProgramsFor(Program... programs) {
        List<FormMapping> formMappings = Arrays.stream(programs).map(program -> new FormMappingBuilder().withProgram(program).build()).collect(Collectors.toList());
        FormMappingRepository formMappingRepository = mock(FormMappingRepository.class);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(any(), any())).thenReturn(formMappings);
        return new ProgramService(null, null, formMappingRepository, ruleService, new EligibilityCheckService(ruleFailureTelemetryService)).getEligiblePrograms(subjectWithoutEnrolments);
    }

    private static EligibilityRuleResponseEntity failedResponse(RuleError error) {
        EligibilityRuleResponseEntity response = new EligibilityRuleResponseEntity();
        response.setStatus("failure");
        response.setError(error);
        return response;
    }

    private static RuleError ruleError(String message) {
        RuleError error = new RuleError();
        error.setMessage(message);
        error.setStack("Error: " + message);
        return error;
    }
}

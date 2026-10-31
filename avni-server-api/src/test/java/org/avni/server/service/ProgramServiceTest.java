package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.RuleExecutionException;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

    @Test
    public void aProgramWithAThrowingEligibilityRuleDoesNotHideOtherEligiblePrograms() throws RuleExecutionException {
        // avni-server#1079: the batch eligibility call fails because program "2"'s rule throws.
        // Program "1" (rule fine) must still be returned, not hidden along with the bad one.
        Program program1 = new ProgramBuilder().withUuid("1").allowMultipleEnrolments(false).build();
        Program program2 = new ProgramBuilder().withUuid("2").allowMultipleEnrolments(false).build();
        Individual subject = new SubjectBuilder().build();   // not enrolled in either -> both reach the rule

        FormMapping fm1 = new FormMappingBuilder().withProgram(program1).build();
        FormMapping fm2 = new FormMappingBuilder().withProgram(program2).build();
        FormMappingRepository formMappingRepository = mock(FormMappingRepository.class);
        when(formMappingRepository.findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(any(), any()))
                .thenReturn(Arrays.asList(fm1, fm2));

        RuleService ruleService = mock(RuleService.class);
        when(ruleService.executeProgramEligibilityCheckRule(any(), any())).thenAnswer(invocation -> {
            List<Program> programs = invocation.getArgument(1);
            if (programs.size() > 1) throw new RuleExecutionException(null);            // batch call fails
            Program p = programs.get(0);
            if (p.getUuid().equals("2")) throw new RuleExecutionException(null);        // program 2's rule throws
            return new EligibilityRuleResponseEntity(Collections.singletonList(new EligibilityRuleEntity(true, p.getUuid())));
        });

        ProgramService programService = new ProgramService(null, null, formMappingRepository, ruleService);
        List<Program> eligiblePrograms = programService.getEligiblePrograms(subject);

        assertEquals(1, eligiblePrograms.size());
        assertEquals("1", eligiblePrograms.get(0).getUuid());
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
        ProgramService programService = new ProgramService(null, null, formMappingRepository, ruleService);
        return programService.getEligiblePrograms(subject);
    }
}

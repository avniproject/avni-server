package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Individual;
import org.avni.server.domain.Program;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.factory.metadata.FormMappingBuilder;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.factory.txn.ProgramEnrolmentBuilder;
import org.avni.server.domain.factory.txn.SubjectBuilder;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.junit.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProgramServiceTest {
    @Test
    public void multipleEnrolmentsProgramsAreStaticallyAlwaysEligible() {
        Program program = new ProgramBuilder().withUuid("1").allowMultipleEnrolments(true).build();
        List<Program> eligiblePrograms = getEligiblePrograms(program);
        assertEquals(1, eligiblePrograms.size());
    }

    @Test
    public void nonMultipleEnrolmentsProgramsAreStaticallyNotEligible() {
        Program program = new ProgramBuilder().withUuid("1").allowMultipleEnrolments(false).build();
        List<Program> eligiblePrograms = getEligiblePrograms(program);
        assertEquals(0, eligiblePrograms.size());
    }

    public List<Program> getEligiblePrograms(Program program) {
        ProgramEnrolment enrolment = new ProgramEnrolmentBuilder().program(program).build();
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

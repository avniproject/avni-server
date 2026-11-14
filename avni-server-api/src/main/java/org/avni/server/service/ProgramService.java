package org.avni.server.service;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.OperationalProgramRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.enums.ruleFailure.SourceType;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.request.OperationalProgramContract;
import org.avni.server.web.request.OperationalProgramsContract;
import org.avni.server.web.request.ProgramRequest;
import org.avni.server.web.request.rules.response.EligibilityRuleEntity;
import org.avni.server.web.request.rules.response.EligibilityRuleResponseEntity;
import org.avni.server.web.request.rules.response.RuleError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProgramService implements NonScopeAwareService {
    private final Logger logger;
    private final ProgramRepository programRepository;
    private final OperationalProgramRepository operationalProgramRepository;
    private final FormMappingRepository formMappingRepository;
    private final RuleService ruleService;
    private final RuleFailureTelemetryService ruleFailureTelemetryService;

    @Autowired
    public ProgramService(ProgramRepository programRepository, OperationalProgramRepository operationalProgramRepository, FormMappingRepository formMappingRepository, RuleService ruleService, RuleFailureTelemetryService ruleFailureTelemetryService) {
        this.programRepository = programRepository;
        this.operationalProgramRepository = operationalProgramRepository;
        this.formMappingRepository = formMappingRepository;
        this.ruleService = ruleService;
        this.ruleFailureTelemetryService = ruleFailureTelemetryService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public void saveProgram(ProgramRequest programRequest) {
        logger.info(String.format("Creating program: %s", programRequest.toString()));
        Program program = programRepository.findByUuid(programRequest.getUuid());
        if (program == null) {
            program = createProgram(programRequest);
        }

        this.updateAndSaveProgram(program, programRequest);
        programRepository.save(program);
    }

    public Program updateAndSaveProgram(Program program, ProgramContract programContract) {
        program.setName(programContract.getName());
        program.setColour(programContract.getColour());
        program.setEnrolmentSummaryRule(programContract.getEnrolmentSummaryRule());
        program.setEnrolmentEligibilityCheckRule(programContract.getEnrolmentEligibilityCheckRule());
        program.setEnrolmentEligibilityCheckDeclarativeRule(programContract.getEnrolmentEligibilityCheckDeclarativeRule());
        program.setActive(programContract.getActive());
        program.setManualEligibilityCheckRequired(programContract.isManualEligibilityCheckRequired());
        program.setShowGrowthChart(programContract.isShowGrowthChart());
        program.setManualEnrolmentEligibilityCheckRule(programContract.getManualEnrolmentEligibilityCheckRule());
        program.setManualEnrolmentEligibilityCheckDeclarativeRule(programContract.getManualEnrolmentEligibilityCheckDeclarativeRule());
        program.setAllowMultipleEnrolments(programContract.isAllowMultipleEnrolments());
        program.setVoided(programContract.isVoided());
        return programRepository.save(program);
    }

    public List<Program> getEligiblePrograms(Individual individual) {
        List<FormMapping> formMappings = formMappingRepository
                .findBySubjectTypeAndFormFormTypeAndIsVoidedFalse(individual.getSubjectType(), FormType.ProgramEnrolment);

        List<Program> activePrograms = individual.getActivePrograms();
        List<Program> staticallyEnrollablePrograms = formMappings.stream()
                .filter(formMapping -> activePrograms.stream().noneMatch(program ->
                        (Objects.equals(formMapping.getProgramUuid(), program.getUuid())) && (!program.isAllowMultipleEnrolments())))
                .map(FormMapping::getProgram)
                .collect(Collectors.toList());

        return getEligibleProgramsFromInactivePrograms(individual, staticallyEnrollablePrograms);
    }

    public void createOperationalProgram(OperationalProgramContract operationalProgramContract, Organisation organisation) {
        String programUuid = operationalProgramContract.getProgram().getUuid();
        Program program = programRepository.findByUuid(programUuid);
        if (program == null) {
            logger.info(String.format("Program not found for uuid: '%s'", programUuid));
            return;
        }

        OperationalProgram operationalProgram = operationalProgramRepository.findByUuid(operationalProgramContract.getUuid());

        if (operationalProgram == null) { /* backward compatibility with old data created by old contract w/o uuid */
            operationalProgram = operationalProgramRepository.findByProgramAndOrganisationId(program, organisation.getId());
        }

        if (operationalProgram == null) {
            operationalProgram = new OperationalProgram();
        }

        operationalProgram.setUuid(operationalProgramContract.getUuid());
        operationalProgram.setName(operationalProgramContract.getName());
        operationalProgram.setProgram(program);
        operationalProgram.setOrganisationId(organisation.getId());
        operationalProgram.setVoided(operationalProgramContract.isVoided());
        operationalProgram.setProgramSubjectLabel(operationalProgramContract.getProgramSubjectLabel());
        operationalProgramRepository.save(operationalProgram);
    }

    private Program createProgram(ProgramRequest programRequest) {
        Program program = new Program();
        program.setUuid(programRequest.getUuid());
        return program;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return programRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public Stream<Program> getAll() {
        return operationalProgramRepository.findAllByIsVoidedFalse().stream().map(OperationalProgram::getProgram);
    }

    private List<Program> getEligibleProgramsFromInactivePrograms(Individual individual, List<Program> staticallyEnrolablePrograms) {
        EligibilityRuleResponseEntity eligibilityRuleResponseEntity;
        try {
            eligibilityRuleResponseEntity = ruleService.executeProgramEligibilityCheckRule(individual, staticallyEnrolablePrograms);
        } catch (RuleExecutionException e) {
            return new ArrayList<>();
        }

        if (eligibilityRuleResponseEntity.hasNoResults()) {
            RuleError error = eligibilityRuleResponseEntity.getError();
            logger.error("Enrolment eligibility check failed for subject {}: {}", individual.getUuid(), error == null ? null : error.getMessage());
            return new ArrayList<>();
        }
        return getEligiblePrograms(individual, staticallyEnrolablePrograms, eligibilityRuleResponseEntity);
    }

    public void savePrograms(ProgramRequest[] programRequests) {
        for (ProgramRequest programRequest : programRequests) {
            try {
                this.saveProgram(programRequest);
            } catch (Exception e) {
                throw new BulkItemSaveException(programRequest, e);
            }
        }
    }

    public void saveOperationalPrograms(OperationalProgramsContract operationalProgramsContract, Organisation organisation) {
        for (OperationalProgramContract opc : operationalProgramsContract.getOperationalPrograms()) {
            try {
                this.createOperationalProgram(opc, organisation);
            } catch (Exception e) {
                throw new BulkItemSaveException(opc, e);
            }
        }
    }

    private List<Program> getEligiblePrograms(Individual individual, List<Program> programs, EligibilityRuleResponseEntity eligibilityRuleResponseEntity) {
        Map<String, EligibilityRuleEntity> eligibilityRuleEntities = eligibilityRuleResponseEntity.getEligibilityRuleEntities().stream()
                .collect(Collectors.toMap(EligibilityRuleEntity::getTypeUUID, Function.identity(), (first, second) -> first));
        return programs.stream()
                .filter(program -> {
                    EligibilityRuleEntity eligibilityRuleEntity = eligibilityRuleEntities.get(program.getUuid());
                    if (eligibilityRuleEntity == null) return false;
                    if (eligibilityRuleEntity.hasFailed())
                        return isEligibleAfterRuleFailure(individual, program, eligibilityRuleEntity.getError());
                    return eligibilityRuleEntity.isEligible();
                })
                .collect(Collectors.toList());
    }

    private boolean isEligibleAfterRuleFailure(Individual individual, Program program, RuleError error) {
        logger.warn("Enrolment eligibility rule of program {} failed for subject {}; treating the program as eligible", program.getUuid(), individual.getUuid());
        try {
            ruleFailureTelemetryService.recordEligibilityRuleFailure(individual, program.getUuid(), SourceType.EnrolmentEligibilityCheck, error);
        } catch (RuntimeException e) {
            logger.error("Could not record the failure of the enrolment eligibility rule of program {}", program.getUuid(), e);
        }
        return true;
    }
}

package org.avni.server.importer.batch.sync.attributes;

import org.avni.server.domain.SubjectType;
import org.avni.server.dao.*;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@JobScope
public class UpdateSyncAttributesTasklet implements Tasklet {
    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final EncounterRepository encounterRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final ProgramEncounterRepository programEncounterRepository;
    private final GroupSubjectRepository groupSubjectRepository;

    @Value("#{jobParameters['subjectTypeId']}")
    private Long subjectTypeId;

    @Autowired
    public UpdateSyncAttributesTasklet(EntityApprovalStatusRepository entityApprovalStatusRepository,
                                       SubjectTypeRepository subjectTypeRepository,
                                       IndividualRepository individualRepository,
                                       EncounterRepository encounterRepository,
                                       ProgramEnrolmentRepository programEnrolmentRepository,
                                       ProgramEncounterRepository programEncounterRepository,
                                       GroupSubjectRepository groupSubjectRepository) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.encounterRepository = encounterRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.groupSubjectRepository = groupSubjectRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution stepContribution, ChunkContext chunkContext) {
        SubjectType subjectType = subjectTypeRepository.findOne(subjectTypeId);
        String syncRegistrationConcept1 = subjectType.getSyncRegistrationConcept1();
        String syncRegistrationConcept2 = subjectType.getSyncRegistrationConcept2();
        individualRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        encounterRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        programEnrolmentRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        programEncounterRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        groupSubjectRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        entityApprovalStatusRepository.updateConceptSyncAttributesForSubjectType(subjectTypeId, syncRegistrationConcept1, syncRegistrationConcept2);
        return RepeatStatus.FINISHED;
    }
}

package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.IndividualController;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Objects;

@Service
public class SubjectMigrationService implements ScopeAwareService {

    private static org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private EntityApprovalStatusRepository entityApprovalStatusRepository;
    private SubjectMigrationRepository subjectMigrationRepository;
    private SubjectTypeRepository subjectTypeRepository;
    private IndividualRepository individualRepository;
    private EncounterRepository encounterRepository;
    private ProgramEnrolmentRepository programEnrolmentRepository;
    private ProgramEncounterRepository programEncounterRepository;
    private GroupSubjectRepository groupSubjectRepository;
    private AddressLevelService addressLevelService;

    @Autowired
    public SubjectMigrationService(EntityApprovalStatusRepository entityApprovalStatusRepository,
                                   SubjectMigrationRepository subjectMigrationRepository,
                                   SubjectTypeRepository subjectTypeRepository,
                                   IndividualRepository individualRepository,
                                   EncounterRepository encounterRepository,
                                   ProgramEnrolmentRepository programEnrolmentRepository,
                                   ProgramEncounterRepository programEncounterRepository,
                                   GroupSubjectRepository groupSubjectRepository, AddressLevelService addressLevelService) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.subjectMigrationRepository = subjectMigrationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.encounterRepository = encounterRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.addressLevelService = addressLevelService;
    }

    @Override
    public OperatingIndividualScopeAwareRepository repository() {
        return subjectMigrationRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUUID) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        User user = UserContextHolder.getUserContext().getUser();
        return subjectType != null && isChangedBySubjectTypeRegistrationLocationType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncParameters.SyncEntityName.SubjectMigration);
    }

    @Transactional
    public void markSubjectMigrationIfRequired(String individualUuid, AddressLevel newAddressLevel, ObservationCollection newObservations) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null || newAddressLevel == null) {
            return;
        }
        SubjectType subjectType = individual.getSubjectType();
        String syncConcept1 = subjectType.getSyncRegistrationConcept1();
        String syncConcept2 = subjectType.getSyncRegistrationConcept2();
        ObservationCollection oldObservations = individual.getObservations();
        String oldObsSingleStringValueSyncConcept1 = oldObservations.getObjectAsSingleStringValue(syncConcept1);
        String newObsSingleStringValueSyncConcept1 = newObservations.getObjectAsSingleStringValue(syncConcept1);
        String oldObsSingleStringValueSyncConcept2 = oldObservations.getObjectAsSingleStringValue(syncConcept2);
        String newObsSingleStringValueSyncConcept2 = newObservations.getObjectAsSingleStringValue(syncConcept2);
        if (!Objects.equals(individual.getAddressLevel().getId(), newAddressLevel.getId()) ||
                !Objects.equals(oldObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept1) ||
                !Objects.equals(oldObsSingleStringValueSyncConcept2, newObsSingleStringValueSyncConcept2)) {
            logger.info(String.format("Migrating subject with UUID %s from %s to %s", individualUuid, addressLevelService.getTitleLineage(individual.getAddressLevel()), addressLevelService.getTitleLineage(newAddressLevel)));
            SubjectMigration subjectMigration = new SubjectMigration();
            subjectMigration.assignUUID();
            subjectMigration.setIndividual(individual);
            subjectMigration.setSubjectType(individual.getSubjectType());
            subjectMigration.setOldAddressLevel(individual.getAddressLevel());
            subjectMigration.setNewAddressLevel(newAddressLevel);
            if (!Objects.equals(oldObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept1)) {
                subjectMigration.setOldSyncConcept1Value(oldObsSingleStringValueSyncConcept1);
                subjectMigration.setNewSyncConcept1Value(newObsSingleStringValueSyncConcept1);
            }
            if (!Objects.equals(oldObsSingleStringValueSyncConcept2, newObsSingleStringValueSyncConcept2)) {
                subjectMigration.setOldSyncConcept2Value(oldObsSingleStringValueSyncConcept2);
                subjectMigration.setNewSyncConcept2Value(newObsSingleStringValueSyncConcept2);
            }
            subjectMigrationRepository.save(subjectMigration);
            encounterRepository.updateSyncAttributesForIndividual(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            programEnrolmentRepository.updateSyncAttributesForIndividual(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            programEncounterRepository.updateSyncAttributes(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            groupSubjectRepository.updateSyncAttributesForGroupSubject(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            entityApprovalStatusRepository.updateSyncAttributesForIndividual(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            groupSubjectRepository.updateSyncAttributesForMemberSubject(individual.getId(), newAddressLevel.getId());
        }
    }
}


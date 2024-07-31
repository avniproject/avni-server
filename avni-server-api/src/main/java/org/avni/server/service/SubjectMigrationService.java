package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.IndividualController;
import org.avni.server.web.request.SubjectMigrationRequest;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;

@Service
public class SubjectMigrationService implements ScopeAwareService<SubjectMigration> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final SubjectMigrationRepository subjectMigrationRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final IndividualRepository individualRepository;
    private final EncounterRepository encounterRepository;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final ProgramEncounterRepository programEncounterRepository;
    private final GroupSubjectRepository groupSubjectRepository;
    private final AddressLevelService addressLevelService;
    private final ChecklistRepository checklistRepository;
    private final ChecklistItemRepository checklistItemRepository;
    private final IndividualRelationshipRepository individualRelationshipRepository;
    private final AccessControlService accessControlService;
    private final LocationRepository locationRepository;

    public enum BulkSubjectMigrationModes {
        byAddress,
        bySyncConcept
    }


    @Autowired
    public SubjectMigrationService(EntityApprovalStatusRepository entityApprovalStatusRepository,
                                   SubjectMigrationRepository subjectMigrationRepository,
                                   SubjectTypeRepository subjectTypeRepository,
                                   IndividualRepository individualRepository,
                                   EncounterRepository encounterRepository,
                                   ProgramEnrolmentRepository programEnrolmentRepository,
                                   ProgramEncounterRepository programEncounterRepository,
                                   GroupSubjectRepository groupSubjectRepository, AddressLevelService addressLevelService,
                                   ChecklistRepository checklistRepository,
                                   ChecklistItemRepository checklistItemRepository,
                                   IndividualRelationshipRepository individualRelationshipRepository, AccessControlService accessControlService, LocationRepository locationRepository) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.subjectMigrationRepository = subjectMigrationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.individualRepository = individualRepository;
        this.encounterRepository = encounterRepository;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.programEncounterRepository = programEncounterRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.addressLevelService = addressLevelService;
        this.checklistRepository = checklistRepository;
        this.checklistItemRepository = checklistItemRepository;
        this.individualRelationshipRepository = individualRelationshipRepository;
        this.accessControlService = accessControlService;
        this.locationRepository = locationRepository;
    }

    @Override
    public OperatingIndividualScopeAwareRepository<SubjectMigration> repository() {
        return subjectMigrationRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUUID) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        User user = UserContextHolder.getUserContext().getUser();
        return subjectType != null && isChangedBySubjectTypeRegistrationLocationType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncEntityName.SubjectMigration);
    }

     /* Setting of old and new sync concept value conditionally based on oldObsValueForSyncConcept and newObsValueForSyncConcept being different creates a problem.
     This means that new and old values are set to null when address is changed but obs values haven't changed. Now these entries get picked by anyone who has the same address but may not have same sync concept values in user settings.
     This can be fixed by picking up actual obs value from individual and putting in old and new values where-ever it is null. Once this optimisation is done we can remove adding of "is null" condition as predicate in subject migration strategy for sync attributes.
     This problem is illustrated in this test - org.avni.server.dao.SubjectMigrationIntegrationTest.migrations_created_by_one_user_is_returned_for_another_user_even_when_concept_attributes_dont_match
     At this moment the performance of this seems small as other filters help in reducing the number of records. Functionally this is not an issue because mobile app does the checks before applying subject migration. */
    @Transactional
    public void markSubjectMigrationIfRequired(String individualUuid, AddressLevel oldAddressLevel, AddressLevel newAddressLevel, ObservationCollection oldObservations, ObservationCollection newObservations, boolean executingInBulk) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null || newAddressLevel == null) {
            return;
        }
        SubjectType subjectType = individual.getSubjectType();
        String syncConcept1 = subjectType.getSyncRegistrationConcept1();
        String syncConcept2 = subjectType.getSyncRegistrationConcept2();
        if (oldObservations == null) oldObservations = individual.getObservations();
        String oldObsSingleStringValueSyncConcept1 = oldObservations.getObjectAsSingleStringValue(syncConcept1);
        String newObsSingleStringValueSyncConcept1 = newObservations.getObjectAsSingleStringValue(syncConcept1);
        String oldObsSingleStringValueSyncConcept2 = oldObservations.getObjectAsSingleStringValue(syncConcept2);
        String newObsSingleStringValueSyncConcept2 = newObservations.getObjectAsSingleStringValue(syncConcept2);
        if (oldAddressLevel == null) oldAddressLevel = individual.getAddressLevel();
        if (!Objects.equals(oldAddressLevel.getId(), newAddressLevel.getId()) ||
                !Objects.equals(oldObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept1) ||
                !Objects.equals(oldObsSingleStringValueSyncConcept2, newObsSingleStringValueSyncConcept2)) {
            logger.info(String.format("Migrating subject with UUID %s from %s to %s", individualUuid, addressLevelService.getTitleLineage(individual.getAddressLevel()), addressLevelService.getTitleLineage(newAddressLevel)));
            SubjectMigration subjectMigration = new SubjectMigration();
            subjectMigration.assignUUID();
            subjectMigration.setIndividual(individual);
            subjectMigration.setSubjectType(individual.getSubjectType());
            subjectMigration.setOldAddressLevel(oldAddressLevel);
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
            if (!executingInBulk) {
                groupSubjectRepository.updateSyncAttributesForMemberSubject(individual.getId(), newAddressLevel.getId());
            }
            entityApprovalStatusRepository.updateSyncAttributesForIndividual(individual.getId(), newAddressLevel.getId(), newObsSingleStringValueSyncConcept1, newObsSingleStringValueSyncConcept2);
            checklistItemRepository.setChangedForSync(individual);
            checklistRepository.setChangedForSync(individual);
            individualRelationshipRepository.setChangedForSync(individual);
        }
    }

    @Transactional
    public void changeSubjectAddressLevel(Individual subject, AddressLevel destAddressLevel) {
        this.markSubjectMigrationIfRequired(subject.getUuid(), null, destAddressLevel, null, subject.getObservations(), true);
        subject.setAddressLevel(destAddressLevel);
        individualRepository.saveEntity(subject);
    }

    public Set<String> bulkMigrate(BulkSubjectMigrationModes mode, SubjectMigrationRequest subjectMigrationRequest) {
        if (mode == BulkSubjectMigrationModes.byAddress) {
            return bulkMigrateByAddress(subjectMigrationRequest.getSubjectUuids(), subjectMigrationRequest.getDestinationAddresses());
        } else {
            return bulkMigrateBySyncConcept(subjectMigrationRequest.getSubjectUuids(), subjectMigrationRequest.getDestinationSyncConcepts());
        }
    }

    public Set<String> bulkMigrateByAddress(List<String> subjectUuids, Map<String, String> destinationAddresses) {
//        Set<String> migrationFailedSubjectUuids = new HashSet<>();
        Set<String> migrationCompletedSubjectUuids = new HashSet<>();
        for (Map.Entry<String, String> destinationAddressEntry : destinationAddresses.entrySet()) {
            Long source = Long.parseLong(destinationAddressEntry.getKey());
            Long dest = Long.parseLong(destinationAddressEntry.getValue());

            AddressLevel sourceAddressLevel = locationRepository.findOne(source);
            AddressLevel destAddressLevel = locationRepository.findOne(dest);
            if (sourceAddressLevel == null || destAddressLevel == null) continue;

            List<Individual> subjectsByAddressLevel = individualRepository.findByUuidInAndAddressLevel(subjectUuids, sourceAddressLevel);

            subjectsByAddressLevel.forEach(subject -> {
                try {
                    accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, subject.getSubjectType().getUuid());
                    logger.info(String.format("Migrating subject : %s, for source address: %s, to destination address: %s", subject.getUuid(), source, dest));
                    this.changeSubjectAddressLevel(subject, destAddressLevel);
                    migrationCompletedSubjectUuids.add(subject.getUuid());
                } catch (Exception e) {
//                    migrationFailedSubjectUuids.add(subject.getUuid());
                }
            });
        }
        return migrationCompletedSubjectUuids;
    }

    public Set<String> bulkMigrateBySyncConcept(List<String> subjectUuids, Map<String, String> destinationSyncConcepts) {
        Set<String> migrationCompletedSubjectUuids = new HashSet<>();
        subjectUuids.forEach(subjectUuid -> {
            try {
                Individual subject = individualRepository.findByUuid(subjectUuid);
                if (subject == null) return;
                accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, subject.getSubjectType().getUuid());
                String destinationSyncConcept1Value = destinationSyncConcepts.get(subject.getSubjectType().getSyncRegistrationConcept1());
                String destinationSyncConcept2Value = destinationSyncConcepts.get(subject.getSubjectType().getSyncRegistrationConcept2());
                if (destinationSyncConcept1Value == null && destinationSyncConcept2Value == null) return;
                ObservationCollection newObservations = new ObservationCollection();
                if (destinationSyncConcept1Value != null) {
                    newObservations.put(subject.getSubjectType().getSyncRegistrationConcept1(), destinationSyncConcept1Value);
                }
                if (destinationSyncConcept2Value != null) {
                    newObservations.put(subject.getSubjectType().getSyncRegistrationConcept2(), destinationSyncConcept2Value);
                }
                this.markSubjectMigrationIfRequired(subjectUuid, null, null, null, newObservations, true);
                migrationCompletedSubjectUuids.add(subject.getUuid());
            } catch (Exception e) {
            }
        });
        return migrationCompletedSubjectUuids;
    }
}

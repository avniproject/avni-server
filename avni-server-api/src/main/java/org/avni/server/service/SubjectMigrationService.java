package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.dao.individualRelationship.IndividualRelationshipRepository;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.IndividualController;
import org.avni.server.web.request.BulkSubjectMigrationRequest;
import org.joda.time.DateTime;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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
    private final ConceptRepository conceptRepository;
    private final IndividualService individualService;
    private final AvniJobRepository avniJobRepository;

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
                                   IndividualRelationshipRepository individualRelationshipRepository, AccessControlService accessControlService, LocationRepository locationRepository, ConceptRepository conceptRepository, IndividualService individualService, AvniJobRepository avniJobRepository) {
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
        this.conceptRepository = conceptRepository;
        this.individualService = individualService;
        this.avniJobRepository = avniJobRepository;
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
        logger.info(String.format("Migrating subject : %s, address: %s -> %s", subject.getUuid(), subject.getAddressLevel().getId(), destAddressLevel.getId()));
        this.markSubjectMigrationIfRequired(subject.getUuid(), null, destAddressLevel, null, subject.getObservations(), true);
        subject.setAddressLevel(destAddressLevel);
        individualService.save(subject);
    }

    @Transactional
    public void changeSubjectSyncConceptValues(Individual subject, String destinationSyncConcept1Value, String destinationSyncConcept2Value) {
        logger.info(String.format("Migrating subject: '%s', sync concept 1 value: '%s' -> '%s', sync concept 2 value: '%s' -> '%s'", subject.getUuid(), subject.getSyncConcept1Value(), destinationSyncConcept1Value, subject.getSyncConcept2Value(), destinationSyncConcept2Value));
        ObservationCollection newObservations = buildSyncConceptValueObservations(subject, destinationSyncConcept1Value, destinationSyncConcept2Value);
        this.markSubjectMigrationIfRequired(subject.getUuid(), null, subject.getAddressLevel(), null, newObservations, true);
        subject.addObservations(newObservations);
        individualService.save(subject);
    }

    @Transactional
    public Map<String, String> bulkMigrate(BulkSubjectMigrationModes mode, BulkSubjectMigrationRequest bulkSubjectMigrationRequest) {
        if (mode == BulkSubjectMigrationModes.byAddress) {
            return bulkMigrateByAddress(bulkSubjectMigrationRequest.getSubjectIds(), bulkSubjectMigrationRequest.getDestinationAddresses());
        } else {
            return bulkMigrateBySyncConcept(bulkSubjectMigrationRequest.getSubjectIds(), bulkSubjectMigrationRequest.getDestinationSyncConcepts());
        }
    }

    @Transactional
    public Map<String, String> bulkMigrateByAddress(List<Long> subjectIds, Map<String, String> destinationAddresses) {
        Map<String, String> migrationFailures = new HashMap<>();
        Map<AddressLevel, AddressLevel> addressLevelMap = new HashMap<>();
        for (Map.Entry<String, String> destinationAddressEntry : destinationAddresses.entrySet()) {
            try {
                Long source = Long.parseLong(destinationAddressEntry.getKey());
                Long dest = Long.parseLong(destinationAddressEntry.getValue());

                AddressLevel sourceAddressLevel = locationRepository.findOne(source);
                AddressLevel destAddressLevel = locationRepository.findOne(dest);
                if (sourceAddressLevel != null && destAddressLevel != null) {
                    addressLevelMap.put(sourceAddressLevel, destAddressLevel);
                }
            } catch (NumberFormatException e) {
                //Continue with other destinationAddresses
            }
        }
        subjectIds.forEach(subjectId -> {
            try {
                Individual subject = individualRepository.findOne(subjectId);
                if (subject == null) throw new RuntimeException("Subject not found");

                accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, subject.getSubjectType().getUuid());
                AddressLevel destAddressLevel = addressLevelMap.get(subject.getAddressLevel());
                if (destAddressLevel == null || destAddressLevel.isVoided()) throw new RuntimeException("Destination address level unavailable / voided");
                this.changeSubjectAddressLevel(subject, destAddressLevel);
            } catch (Exception e) {
                logger.debug("Failed to migrate subject {} byAddress", subjectId);
                migrationFailures.put(String.valueOf(subjectId), e.getMessage());
            }
        });
        return migrationFailures;
    }

    @Transactional
    public Map<String, String> bulkMigrateBySyncConcept(List<Long> subjectIds, Map<String, String> destinationSyncConcepts) {
        Map<String, String> migrationFailures = new HashMap<>();
        subjectIds.forEach(subjectId -> {
            try {
                Individual subject = individualRepository.findOne(subjectId);
                if (subject == null) throw new RuntimeException("Subject not found");
                accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, subject.getSubjectType().getUuid());
                String destinationSyncConcept1Value = validateSyncConcept(subject.getSubjectType().getSyncRegistrationConcept1(), subject.getSyncConcept1Value(), destinationSyncConcepts);
                String destinationSyncConcept2Value = validateSyncConcept(subject.getSubjectType().getSyncRegistrationConcept2(), subject.getSyncConcept2Value(), destinationSyncConcepts);
                if (destinationSyncConcept1Value == null && destinationSyncConcept2Value == null) {
                    throw new RuntimeException("Valid destination sync concept(s) not found");
                }
                changeSubjectSyncConceptValues(subject, destinationSyncConcept1Value, destinationSyncConcept2Value);
            } catch (Exception e) {
                logger.debug("Failed to migrate subject {} bySyncConcept", subjectId);
                migrationFailures.put(String.valueOf(subjectId), e.getMessage());
            }
        });
        return migrationFailures;
    }

    private String validateSyncConcept(String subjectTypeSyncConceptUuid, String currentValue, Map<String, String> destinationSyncConcepts) {
        if (subjectTypeSyncConceptUuid == null || //sync concept not configured for subject type
                !destinationSyncConcepts.containsKey(subjectTypeSyncConceptUuid) //sync concept not included in migration
        ) {
            return null;
        }
        String destinationSyncConceptValue = destinationSyncConcepts.get(subjectTypeSyncConceptUuid);
        if (destinationSyncConceptValue == null) {
            return null;
        }
        Concept syncConcept = conceptRepository.findByUuid(subjectTypeSyncConceptUuid);

        if (currentValue != null && Objects.equals(currentValue.trim(), destinationSyncConceptValue.trim())) {
            throw new RuntimeException("Source value and Destination value are the same");
        }

        if (syncConcept.isCoded()) {
            ConceptAnswer conceptAnswer = syncConcept.findConceptAnswerByConceptUUID(destinationSyncConceptValue);
            if (conceptAnswer == null || conceptAnswer.isVoided()) {
                throw new RuntimeException(String.format("Invalid value '%s' for coded sync concept", destinationSyncConceptValue));
            }
        }
        return destinationSyncConceptValue;
    }

    private static ObservationCollection buildSyncConceptValueObservations(Individual subject, String destinationSyncConcept1Value, String destinationSyncConcept2Value) {
        ObservationCollection newObservations = new ObservationCollection();
        //set observation for unchanged values if sync concept exists so unchanged sync concept values are not overwritten
        if (subject.getSubjectType().getSyncRegistrationConcept1() != null) {
            newObservations.put(subject.getSubjectType().getSyncRegistrationConcept1(), destinationSyncConcept1Value != null ? destinationSyncConcept1Value.trim() : subject.getSyncConcept1Value());
        }
        if (subject.getSubjectType().getSyncRegistrationConcept2() != null) {
            newObservations.put(subject.getSubjectType().getSyncRegistrationConcept2(), destinationSyncConcept2Value != null ? destinationSyncConcept2Value.trim() : subject.getSyncConcept2Value());
        }
        return newObservations;
    }

    public JobStatus getBulkSubjectMigrationJobStatus(String jobUuid) {
        String jobFilterCondition = " and uuid = '" + jobUuid + "'";
        Page<JobStatus> jobStatuses = avniJobRepository.getJobStatuses(UserContextHolder.getUser(), jobFilterCondition, PageRequest.of(0, 1));
        return (jobStatuses != null && !jobStatuses.getContent().isEmpty()) ? jobStatuses.getContent().get(0) : null;
    }
}

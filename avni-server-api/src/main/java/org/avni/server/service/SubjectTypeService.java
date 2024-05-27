package org.avni.server.service;

import org.avni.server.application.Subject;
import org.avni.server.application.SubjectTypeSettingKey;
import org.avni.server.dao.AvniJobRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.web.request.OperationalSubjectTypeContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.request.syncAttribute.UserSyncAttributeAssignmentRequest;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SubjectTypeService implements NonScopeAwareService {
    private final Logger logger;
    private final OperationalSubjectTypeRepository operationalSubjectTypeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final Job syncAttributesJob;
    private final JobLauncher syncAttributesJobLauncher;
    private final Job userSubjectTypeCreateJob;
    private final JobLauncher userSubjectTypeCreateJobLauncher;
    private final AvniJobRepository avniJobRepository;
    private final ConceptService conceptService;

    @Autowired
    public SubjectTypeService(SubjectTypeRepository subjectTypeRepository,
                              OperationalSubjectTypeRepository operationalSubjectTypeRepository,
                              Job syncAttributesJob,
                              JobLauncher syncAttributesJobLauncher,
                              Job userSubjectTypeCreateJob,
                              JobLauncher userSubjectTypeCreateJobLauncher,
                              AvniJobRepository avniJobRepository,
                              ConceptService conceptService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.syncAttributesJob = syncAttributesJob;
        this.syncAttributesJobLauncher = syncAttributesJobLauncher;
        this.userSubjectTypeCreateJob = userSubjectTypeCreateJob;
        this.userSubjectTypeCreateJobLauncher = userSubjectTypeCreateJobLauncher;
        this.avniJobRepository = avniJobRepository;
        this.conceptService = conceptService;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public SubjectTypeUpsertResponse saveSubjectType(SubjectTypeContract subjectTypeRequest) {
        logger.info(String.format("Creating subjectType: %s", subjectTypeRequest.toString()));
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeRequest.getUuid());
        boolean isSubjectTypeNotPresentInDB = (subjectType == null);
        if (isSubjectTypeNotPresentInDB) {
            subjectType = new SubjectType();
        }
        subjectType.setUuid(subjectTypeRequest.getUuid());
        subjectType.setVoided(subjectTypeRequest.isVoided());
        subjectType.setName(subjectTypeRequest.getName());
        subjectType.setGroup(subjectTypeRequest.isGroup());
        subjectType.setHousehold(subjectTypeRequest.isHousehold());
        subjectType.setActive(subjectTypeRequest.getActive());
        subjectType.setAllowEmptyLocation(subjectTypeRequest.isAllowEmptyLocation());
        subjectType.setAllowMiddleName(subjectTypeRequest.isAllowMiddleName());
        subjectType.setAllowProfilePicture(subjectTypeRequest.isAllowProfilePicture());
        subjectType.setUniqueName(subjectTypeRequest.isUniqueName());
        subjectType.setValidFirstNameFormat(subjectTypeRequest.getValidFirstNameFormat());
        subjectType.setValidLastNameFormat(subjectTypeRequest.getValidLastNameFormat());
        subjectType.setType(Subject.valueOf(subjectTypeRequest.getType()));
        subjectType.setSubjectSummaryRule(subjectTypeRequest.getSubjectSummaryRule());
        subjectType.setProgramEligibilityCheckRule(subjectTypeRequest.getProgramEligibilityCheckRule());
        subjectType.setProgramEligibilityCheckDeclarativeRule(subjectTypeRequest.getProgramEligibilityCheckDeclarativeRule());
        subjectType.setIconFileS3Key(subjectTypeRequest.getIconFileS3Key());
        subjectType.setShouldSyncByLocation(subjectTypeRequest.isShouldSyncByLocation());
        subjectType.setDirectlyAssignable(subjectTypeRequest.isDirectlyAssignable());
        subjectType.setSyncRegistrationConcept1(subjectTypeRequest.getSyncRegistrationConcept1());
        subjectType.setSyncRegistrationConcept1Usable(subjectTypeRequest.getSyncRegistrationConcept1Usable());
        subjectType.setSyncRegistrationConcept2(subjectTypeRequest.getSyncRegistrationConcept2());
        subjectType.setSyncRegistrationConcept2Usable(subjectTypeRequest.getSyncRegistrationConcept2Usable());
        subjectType.setNameHelpText(subjectTypeRequest.getNameHelpText());
        subjectType.setSettings(subjectTypeRequest.getSettings() != null ? subjectTypeRequest.getSettings() : getDefaultSettings());
        subjectType = subjectTypeRepository.save(subjectType);
        return new SubjectTypeUpsertResponse(isSubjectTypeNotPresentInDB, subjectType);
    }

    public void createOperationalSubjectType(OperationalSubjectTypeContract operationalSubjectTypeContract, Organisation organisation) {
        String subjectTypeUUID = operationalSubjectTypeContract.getSubjectType().getUuid();
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        if (subjectType == null) {
            logger.info(String.format("SubjectType not found for uuid: '%s'", subjectTypeUUID));
        }

        OperationalSubjectType operationalSubjectType = operationalSubjectTypeRepository.findByUuid(operationalSubjectTypeContract.getUuid());

        if (operationalSubjectType == null) {
            operationalSubjectType = new OperationalSubjectType();
        }

        operationalSubjectType.setUuid(operationalSubjectTypeContract.getUuid());
        operationalSubjectType.setName(operationalSubjectTypeContract.getName());
        operationalSubjectType.setSubjectType(subjectType);
        operationalSubjectType.setOrganisationId(organisation.getId());
        operationalSubjectType.setVoided(operationalSubjectTypeContract.isVoided());
        operationalSubjectTypeRepository.save(operationalSubjectType);
    }

    public SubjectType createIndividualSubjectType() {
        SubjectType subjectType = new SubjectType();
        subjectType.assignUUID();
        subjectType.setName("Individual");
        SubjectType savedSubjectType = subjectTypeRepository.save(subjectType);
        saveIndividualOperationalSubjectType(savedSubjectType);
        return savedSubjectType;
    }

    public SubjectType getByName(String name) {
        return subjectTypeRepository.findByName(name);
    }

    private void saveIndividualOperationalSubjectType(SubjectType subjectType) {
        OperationalSubjectType operationalSubjectType = new OperationalSubjectType();
        operationalSubjectType.assignUUID();
        operationalSubjectType.setName(subjectType.getName());
        operationalSubjectType.setSubjectType(subjectType);
        operationalSubjectTypeRepository.save(operationalSubjectType);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return subjectTypeRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public UserSyncAttributeAssignmentRequest getSyncAttributeData() {
        List<SubjectType> subjectTypes = subjectTypeRepository.findAllByIsVoidedFalse();
        List<SubjectType> subjectTypesHavingSyncConcepts = subjectTypes
                .stream()
                .filter(st -> st.getSyncRegistrationConcept1() != null || st.getSyncRegistrationConcept2() != null)
                .collect(Collectors.toList());
        boolean isAnySyncByLocation = subjectTypes.stream().anyMatch(SubjectType::isShouldSyncByLocation);
        return new UserSyncAttributeAssignmentRequest(subjectTypesHavingSyncConcepts, isAnySyncByLocation, conceptService);
    }

    public Stream<SubjectType> getAll() {
        return operationalSubjectTypeRepository.findAllByIsVoidedFalse().stream().map(OperationalSubjectType::getSubjectType);
    }

    public void updateSyncAttributesIfRequired(SubjectType subjectType) {
        Boolean isSyncAttribute1Usable = subjectType.isSyncRegistrationConcept1Usable();
        Boolean isSyncAttribute2Usable = subjectType.isSyncRegistrationConcept2Usable();
        boolean isSyncAttributeChanged = (isSyncAttribute1Usable != null && !isSyncAttribute1Usable) || (isSyncAttribute2Usable != null && !isSyncAttribute2Usable);
        String lastJobStatus = avniJobRepository.getLastJobStatusForSubjectType(subjectType);
        if (isSyncAttributeChanged || (lastJobStatus != null && avniJobRepository.getLastJobStatusForSubjectType(subjectType).equals("FAILED"))) {
            UserContext userContext = UserContextHolder.getUserContext();
            User user = userContext.getUser();
            Organisation organisation = userContext.getOrganisation();
            String jobUUID = UUID.randomUUID().toString();
            JobParameters jobParameters =
                    new JobParametersBuilder()
                            .addString("uuid", jobUUID)
                            .addString("organisationUUID", organisation.getUuid())
                            .addLong("userId", user.getId(), false)
                            .addLong("subjectTypeId", subjectType.getId())
                            .toJobParameters();
            try {
                syncAttributesJobLauncher.run(syncAttributesJob, jobParameters);
            } catch (JobParametersInvalidException | JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException | JobRestartException e) {
                throw new RuntimeException(String.format("Error while starting the sync attribute job, %s", e.getMessage()), e);
            }
        }
    }

    @Transactional(Transactional.TxType.NOT_SUPPORTED)
    public void
    launchUserSubjectTypeJob(SubjectType subjectType) {
        String jobUUID = UUID.randomUUID().toString();
        UserContext userContext = UserContextHolder.getUserContext();
        JobParameters jobParameters = new JobParametersBuilder()
                .addString("uuid", jobUUID)
                .addString("organisationUUID", userContext.getOrganisationUUID())
                .addLong("userId", userContext.getUser().getId())
                .addLong("subjectTypeId", subjectType.getId())
                .toJobParameters();
        try {
            userSubjectTypeCreateJobLauncher.run(userSubjectTypeCreateJob, jobParameters);
        } catch (JobParametersInvalidException | JobExecutionAlreadyRunningException | JobInstanceAlreadyCompleteException | JobRestartException e) {
            throw new RuntimeException(String.format("Error while starting user subject type create job, %s", e.getMessage()), e);
        }
    }

    public List<String> constructSyncAttributeHeadersForSubjectTypes() {
        List<SubjectType> subjectTypes = subjectTypeRepository.findByIsVoidedFalse();
        Predicate<SubjectType> subjectTypeHasSyncAttributes = subjectType ->
                Objects.nonNull(subjectType.getSyncRegistrationConcept1()) ||
                        Objects.nonNull(subjectType.getSyncRegistrationConcept2());
        return subjectTypes.stream().sorted((a,b) -> (int) (a.getId() - b.getId())).
                filter(subjectTypeHasSyncAttributes).
                map(this::constructSyncAttributeHeadersForSubjectType).
                flatMap(Collection::stream).
                collect(Collectors.toList());
    }

    private List<String> constructSyncAttributeHeadersForSubjectType(SubjectType subjectTypeWithSyncAttribute) {
        String[] syncAttributes = new String[]{subjectTypeWithSyncAttribute.getSyncRegistrationConcept1(),
                subjectTypeWithSyncAttribute.getSyncRegistrationConcept2()};

        return Arrays.stream(syncAttributes).
                filter(Objects::nonNull).sorted().
                map(sa -> String.format("%s->%s", subjectTypeWithSyncAttribute.getName(), conceptService.get(sa).getName())).
                collect(Collectors.toList());
    }

    public List<String> constructSyncAttributeAllowedValuesForSubjectTypes() {
        List<SubjectType> subjectTypes = subjectTypeRepository.findByIsVoidedFalse();
        Predicate<SubjectType> subjectTypeHasSyncAttributes = subjectType ->
                Objects.nonNull(subjectType.getSyncRegistrationConcept1()) ||
                        Objects.nonNull(subjectType.getSyncRegistrationConcept2());
        return subjectTypes.stream().sorted((a,b) -> (int) (a.getId() - b.getId())).
                filter(subjectTypeHasSyncAttributes).
                map(this::constructSyncAttributeAllowedValuesForSubjectType).
                flatMap(Collection::stream).
                collect(Collectors.toList());
    }

    private List<String> constructSyncAttributeAllowedValuesForSubjectType(SubjectType subjectTypeWithSyncAttribute) {
        String[] syncAttributes = new String[]{subjectTypeWithSyncAttribute.getSyncRegistrationConcept1(),
                subjectTypeWithSyncAttribute.getSyncRegistrationConcept2()};

        return Arrays.stream(syncAttributes).
                filter(Objects::nonNull).sorted().
                map(sa -> String.format("\"Allowed values: %s\"", conceptService.getSampleValuesForSyncConcept(conceptService.get(sa))))
                .collect(Collectors.toList());
    }

    public JsonObject getDefaultSettings() {
        JsonObject defaultSettings = new JsonObject();
        defaultSettings.put(String.valueOf(SubjectTypeSettingKey.displayPlannedEncounters), true);
        defaultSettings.put(String.valueOf(SubjectTypeSettingKey.displayRegistrationDetails), true);
        return defaultSettings;
    }

    public static class SubjectTypeUpsertResponse {
        boolean isSubjectTypeNotPresentInDB;
        SubjectType subjectType;

        public SubjectTypeUpsertResponse(boolean isSubjectTypeNotPresentInDB, SubjectType subjectType) {
            this.isSubjectTypeNotPresentInDB = isSubjectTypeNotPresentInDB;
            this.subjectType = subjectType;
        }

        public boolean isSubjectTypeNotPresentInDB() {
            return isSubjectTypeNotPresentInDB;
        }

        public SubjectType getSubjectType() {
            return subjectType;
        }
    }
}

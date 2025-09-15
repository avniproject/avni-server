package org.avni.server.service;

import jakarta.transaction.Transactional;
import org.avni.server.application.Subject;
import org.avni.server.application.SubjectTypeSettingKey;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.AvniJobRepository;
import org.avni.server.dao.OperationalSubjectTypeRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.batch.BatchJobService;
import org.avni.server.web.request.OperationalSubjectTypeContract;
import org.avni.server.web.request.OperationalSubjectTypesContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.request.syncAttribute.UserSyncAttributeAssignmentRequest;
import org.avni.server.domain.SubjectTypeSetting;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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
    private final AvniJobRepository avniJobRepository;
    private final Job userSubjectTypeCreateJob;
    private final JobLauncher userSubjectTypeCreateJobLauncher;
    private final ConceptService conceptService;
    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final UserService userService;
    private final LocationHierarchyService locationHierarchyService;
    private final BatchJobService batchJobService;

    @Autowired
    public SubjectTypeService(SubjectTypeRepository subjectTypeRepository,
                              OperationalSubjectTypeRepository operationalSubjectTypeRepository,
                              Job syncAttributesJob,
                              JobLauncher syncAttributesJobLauncher,
                              Job userSubjectTypeCreateJob,
                              JobLauncher userSubjectTypeCreateJobLauncher,
                              AvniJobRepository avniJobRepository,
                              ConceptService conceptService, OrganisationConfigService organisationConfigService,
                              AddressLevelTypeRepository addressLevelTypeRepository, UserService userService, LocationHierarchyService locationHierarchyService,
                              BatchJobService batchJobService) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.operationalSubjectTypeRepository = operationalSubjectTypeRepository;
        this.syncAttributesJob = syncAttributesJob;
        this.syncAttributesJobLauncher = syncAttributesJobLauncher;
        this.userSubjectTypeCreateJob = userSubjectTypeCreateJob;
        this.userSubjectTypeCreateJobLauncher = userSubjectTypeCreateJobLauncher;
        this.avniJobRepository = avniJobRepository;
        this.conceptService = conceptService;
        this.organisationConfigService = organisationConfigService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationHierarchyService = locationHierarchyService;
        this.batchJobService = batchJobService;
        logger = LoggerFactory.getLogger(this.getClass());
        this.userService = userService;
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
        subjectType.setMemberAdditionEligibilityCheckRule(subjectTypeRequest.getMemberAdditionEligibilityCheckRule());
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
        String lastJobStatus = batchJobService.getLastSyncAttributesJobStatus(subjectType);
        if (isSyncAttributeChanged || (lastJobStatus != null && lastJobStatus.equals("FAILED"))) {
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
                .addString("type", String.format("Subjects Create - %s", subjectType.getName()), false)
                .addString("organisationUUID", userContext.getOrganisation().getUuid())
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
                map(sa -> String.format("\"Mandatory field. Allowed values: %s\"", conceptService.getAllowedValuesForSyncConcept(conceptService.get(sa))))
                .collect(Collectors.toList());
    }

    public JsonObject getDefaultSettings() {
        JsonObject defaultSettings = new JsonObject();
        defaultSettings.put(String.valueOf(SubjectTypeSettingKey.displayPlannedEncounters), true);
        defaultSettings.put(String.valueOf(SubjectTypeSettingKey.displayRegistrationDetails), true);
        return defaultSettings;
    }

    public AddressLevelTypes getRegistrableLocationTypes(SubjectType subjectType) {
        OrganisationConfig organisationConfig = this.organisationConfigService.getCurrentOrganisationConfig();
        SubjectTypeSetting registrationSetting = organisationConfig.getRegistrationSetting(subjectType);
        AddressLevelTypes locationTypes = addressLevelTypeRepository.getAllAddressLevelTypes();
        if (locationTypes.isEmpty()) {
            throw new RuntimeException("No address level types found");
        }

        List<String> customRegistrationLevelTypeUuids = (registrationSetting != null && registrationSetting.getLocationTypeUUIDs() != null && !registrationSetting.getLocationTypeUUIDs().isEmpty())
                ? registrationSetting.getLocationTypeUUIDs() : null;
        TreeSet<String> validLocationHierarchies = locationHierarchyService.fetchAndFilterHierarchies();
        List<List<String>> hierarchies = validLocationHierarchies.stream()
                .map(hierarchy -> Arrays.asList(hierarchy.split("\\.")))
                .collect(Collectors.toList());

        if (customRegistrationLevelTypeUuids != null) {
            List<String> customLevelTypeIds = registrationSetting.getAddressLevelTypes(locationTypes).stream().map(alt -> alt.getId().toString()).collect(Collectors.toList());

            Set<String> addressIds = hierarchies.stream()
                    .map(typesInHierarchy -> typesInHierarchy.stream()
                            .map(altId -> customLevelTypeIds.contains(altId) ? typesInHierarchy.subList(typesInHierarchy.indexOf(altId), typesInHierarchy.size()) : null)
                            .filter(Objects::nonNull)
                            .flatMap(Collection::stream)
                            .collect(Collectors.toList()))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toSet());

            return new AddressLevelTypes(locationTypes.stream().filter(alt -> addressIds.contains(alt.getId().toString())).collect(Collectors.toList()));
        } else {
            List<String> lowestAddressIds = hierarchies.stream().map(lh -> lh.get(lh.size() - 1)).collect(Collectors.toList());
            return new AddressLevelTypes(locationTypes.stream().filter(alt -> lowestAddressIds.contains(alt.getId().toString())).collect(Collectors.toList()));
        }
    }

    @Transactional
    public void saveSubjectTypesFromBundle(SubjectTypeContract[] subjectTypeContracts) {
        for (SubjectTypeContract subjectTypeContract : subjectTypeContracts) {
            try {
                SubjectTypeUpsertResponse response = this.saveSubjectType(subjectTypeContract);
                if (response.isSubjectTypeNotPresentInDB() && Subject.valueOf(subjectTypeContract.getType()).equals(Subject.User)) {
                    userService.ensureSubjectsForUserSubjectType(response.getSubjectType());
                }
            } catch (Exception e) {
                throw new BulkItemSaveException(subjectTypeContract, e);
            }
        }
    }

    public void saveOperationalSubjectTypes(OperationalSubjectTypesContract operationalSubjectTypesContract, Organisation organisation) {
        for (OperationalSubjectTypeContract ostc : operationalSubjectTypesContract.getOperationalSubjectTypes()) {
            try {
                this.createOperationalSubjectType(ostc, organisation);
            } catch (Exception e) {
                throw new BulkItemSaveException(ostc, e);
            }
        }
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

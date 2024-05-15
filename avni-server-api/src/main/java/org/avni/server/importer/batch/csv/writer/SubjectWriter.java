package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.GenderRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.jadira.usertype.spi.utils.lang.StringUtils;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;


@Component
public class SubjectWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final LocationRepository locationRepository;
    private final IndividualRepository individualRepository;
    private final GenderRepository genderRepository;
    private final SubjectTypeCreator subjectTypeCreator;
    private final LocationCreator locationCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationService observationService;
    private final RuleServerInvoker ruleServerInvoker;
    private final VisitCreator visitCreator;
    private final DecisionCreator decisionCreator;
    private final ObservationCreator observationCreator;
    private final IndividualService individualService;
    private final S3Service s3Service;
    private final EntityApprovalStatusWriter entityApprovalStatusWriter;
    private AddressLevelCreator addressLevelCreator;
    private final SubjectMigrationService subjectMigrationService;

    private static final Logger logger = LoggerFactory.getLogger(SubjectWriter.class);

    @Autowired
    public SubjectWriter(AddressLevelTypeRepository addressLevelTypeRepository,
                         LocationRepository locationRepository,
                         IndividualRepository individualRepository,
                         GenderRepository genderRepository,
                         SubjectTypeCreator subjectTypeCreator,
                         FormMappingRepository formMappingRepository,
                         ObservationService observationService,
                         RuleServerInvoker ruleServerInvoker,
                         VisitCreator visitCreator,
                         DecisionCreator decisionCreator,
                         ObservationCreator observationCreator, IndividualService individualService, EntityApprovalStatusWriter entityApprovalStatusWriter,
                         S3Service s3Service,
                         OrganisationConfigService organisationConfigService,
                         AddressLevelCreator addressLevelCreator, SubjectMigrationService subjectMigrationService) {
        super(organisationConfigService);
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationRepository = locationRepository;
        this.individualRepository = individualRepository;
        this.genderRepository = genderRepository;
        this.subjectTypeCreator = subjectTypeCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationService = observationService;
        this.ruleServerInvoker = ruleServerInvoker;
        this.visitCreator = visitCreator;
        this.decisionCreator = decisionCreator;
        this.observationCreator = observationCreator;
        this.individualService = individualService;
        this.entityApprovalStatusWriter = entityApprovalStatusWriter;
        this.addressLevelCreator = addressLevelCreator;
        this.subjectMigrationService = subjectMigrationService;
        this.locationCreator = new LocationCreator();
        this.s3Service = s3Service;
    }

    @Override
    public void write(List<? extends Row> rows) throws Exception {
        for (Row row : rows) write(row);
    }

    private void write(Row row) throws Exception {
        try {
            List<AddressLevelType> locationTypes = addressLevelTypeRepository.findAllByIsVoidedFalse();
            locationTypes.sort(Comparator.comparingDouble(AddressLevelType::getLevel).reversed());

            Individual individual = getOrCreateIndividual(row);
            AddressLevel oldAddressLevel = individual.getAddressLevel();
            ObservationCollection oldObservations = individual.getObservations();
            List<String> allErrorMsgs = new ArrayList<>();

            SubjectType subjectType = subjectTypeCreator.getSubjectType(row.get(SubjectHeaders.subjectTypeHeader), SubjectHeaders.subjectTypeHeader);
            individual.setSubjectType(subjectType);
            individual.setFirstName(row.get(SubjectHeaders.firstName));
            if (subjectType.isAllowMiddleName())
                individual.setMiddleName(row.get(SubjectHeaders.middleName));
            individual.setLastName(row.get(SubjectHeaders.lastName));
            setProfilePicture(subjectType, individual, row, allErrorMsgs);
            setDateOfBirth(individual, row, allErrorMsgs);
            individual.setDateOfBirthVerified(row.getBool(SubjectHeaders.dobVerified));
            setRegistrationDate(individual, row, allErrorMsgs);
            individual.setRegistrationLocation(locationCreator.getLocation(row, SubjectHeaders.registrationLocation, allErrorMsgs));
            individual.setAddressLevel(addressLevelCreator.findAddressLevel(row, locationTypes));
            if (individual.getSubjectType().getType().equals(Subject.Person)) setGender(individual, row);
            FormMapping formMapping = formMappingRepository.getRegistrationFormMapping(subjectType);
            individual.setVoided(false);
            individual.assignUUIDIfRequired();
            if (formMapping == null) {
                throw new Exception(String.format("No form found for the subject type %s", subjectType.getName()));
            }
            Individual savedIndividual;
            if (skipRuleExecution()) {
                SubjectHeaders subjectHeaders = new SubjectHeaders(subjectType);
                individual.setObservations(observationCreator.getObservations(row, subjectHeaders, allErrorMsgs, FormType.IndividualProfile, individual.getObservations()));
                savedIndividual = individualService.save(individual);
            } else {
                UploadRuleServerResponseContract ruleResponse = ruleServerInvoker.getRuleServerResult(row, formMapping.getForm(), individual, allErrorMsgs);
                individual.setObservations(observationService.createObservations(ruleResponse.getObservations()));
                decisionCreator.addRegistrationDecisions(individual.getObservations(), ruleResponse.getDecisions());
                savedIndividual = individualService.save(individual);
                visitCreator.saveScheduledVisits(formMapping.getType(), savedIndividual.getUuid(), null, ruleResponse.getVisitSchedules(), null);
            }
            if (oldAddressLevel != null) { // existing subject is being updated
                subjectMigrationService.markSubjectMigrationIfRequired(savedIndividual.getUuid(), oldAddressLevel, savedIndividual.getAddressLevel(), oldObservations, savedIndividual.getObservations(), false);
            }
            entityApprovalStatusWriter.saveStatus(formMapping, savedIndividual.getId(), EntityApprovalStatus.EntityType.Subject, savedIndividual.getSubjectType().getUuid());
        } catch (Exception e) {
            logger.warn("Error in writing row", e);
            throw e;
        }
    }

    private void setProfilePicture(SubjectType subjectType, Individual individual, Row row, List<String> errorMsgs) {
        try {
            String profilePicUrl = row.get(SubjectHeaders.profilePicture);
            if(StringUtils.isNotEmpty(profilePicUrl) && subjectType.isAllowProfilePicture()) {
                individual.setProfilePicture(s3Service
                        .uploadProfilePic(profilePicUrl, null));
            } else if(StringUtils.isNotEmpty(profilePicUrl)) {
                errorMsgs.add(String.format("Not allowed to set '%s'", SubjectHeaders.profilePicture));
            }
        } catch (Exception e) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeaders.profilePicture));
        }
    }

    private Individual getOrCreateIndividual(Row row) {
        String id = row.get(SubjectHeaders.id);
        Individual existingIndividual = null;
        if (!(id == null || id.isEmpty())) {
            existingIndividual = individualRepository.findByLegacyIdOrUuid(id);
        }
        return existingIndividual == null ? createNewIndividual(id) : existingIndividual;
    }

    private Individual createNewIndividual(String externalId) {
        Individual individual = new Individual();
        individual.setLegacyId(externalId);
        return individual;
    }

    private void setDateOfBirth(Individual individual, Row row, List<String> errorMsgs) {
        try {
            String dob = row.get(SubjectHeaders.dateOfBirth);
            if (dob != null && !dob.trim().isEmpty())
                individual.setDateOfBirth(LocalDate.parse(dob));
        } catch (Exception ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeaders.dateOfBirth));
        }
    }

    private void setRegistrationDate(Individual individual, Row row, List<String> errorMsgs) {
        try {
            String registrationDate = row.get(SubjectHeaders.registrationDate);
            individual.setRegistrationDate(registrationDate != null && !registrationDate.trim().isEmpty() ? LocalDate.parse(registrationDate) : LocalDate.now());
        } catch (Exception ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeaders.registrationDate));
        }
    }

    private void setGender(Individual individual, Row row) throws Exception {
        try {
            String genderName = row.get(SubjectHeaders.gender);
            Gender gender = genderRepository.findByNameIgnoreCase(genderName);
            if (gender == null) {
                throw new Exception(String.format("Invalid '%s' - '%s'", SubjectHeaders.gender, genderName));
            }
            individual.setGender(gender);
        } catch (Exception ex) {
            throw new Exception(String.format("Invalid '%s'", SubjectHeaders.gender));
        }
    }
}

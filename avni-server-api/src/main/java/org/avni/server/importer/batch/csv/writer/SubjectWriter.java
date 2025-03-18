package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.dao.GenderRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.importer.batch.csv.contract.UploadRuleServerResponseContract;
import org.avni.server.importer.batch.csv.creator.*;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@Component
public class SubjectWriter extends EntityWriter implements ItemWriter<Row>, Serializable {
    private final IndividualRepository individualRepository;
    private final GenderRepository genderRepository;
    private final SubjectTypeCreator subjectTypeCreator;
    private final FormMappingRepository formMappingRepository;
    private final ObservationService observationService;
    private final RuleServerInvoker ruleServerInvoker;
    private final VisitCreator visitCreator;
    private final DecisionCreator decisionCreator;
    private final ObservationCreator observationCreator;
    private final IndividualService individualService;
    private final S3Service s3Service;
    private final EntityApprovalStatusWriter entityApprovalStatusWriter;
    private final AddressLevelCreator addressLevelCreator;
    private final SubjectMigrationService subjectMigrationService;
    private final SubjectTypeService subjectTypeService;
    private final SubjectHeadersCreator subjectHeadersCreator;

    private static final Logger logger = LoggerFactory.getLogger(SubjectWriter.class);

    @Autowired
    public SubjectWriter(IndividualRepository individualRepository,
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
                         AddressLevelCreator addressLevelCreator, SubjectMigrationService subjectMigrationService, SubjectTypeService subjectTypeService, SubjectHeadersCreator subjectHeadersCreator) {
        super(organisationConfigService);
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
        this.subjectTypeService = subjectTypeService;
        this.s3Service = s3Service;
        this.subjectHeadersCreator = subjectHeadersCreator;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws Exception {
        for (Row row : chunk.getItems()) write(row);
    }

    private void write(Row row) throws Exception {
        try {
            Individual individual = getOrCreateIndividual(row);
            AddressLevel oldAddressLevel = individual.getAddressLevel();
            ObservationCollection oldObservations = individual.getObservations();
            List<String> allErrorMsgs = new ArrayList<>();

            SubjectType subjectType = subjectTypeCreator.getSubjectType(row.get(SubjectHeadersCreator.subjectTypeHeader), SubjectHeadersCreator.subjectTypeHeader);
            individual.setSubjectType(subjectType);
            individual.setFirstName(row.get(SubjectHeadersCreator.firstName));
            if (subjectType.isAllowMiddleName())
                individual.setMiddleName(row.get(SubjectHeadersCreator.middleName));
            individual.setLastName(row.get(SubjectHeadersCreator.lastName));
            setProfilePicture(subjectType, individual, row, allErrorMsgs);
            setDateOfBirth(individual, row, allErrorMsgs);
            Boolean dobVerified = row.getBool(SubjectHeadersCreator.dobVerified);
            individual.setDateOfBirthVerified(dobVerified != null ? dobVerified : false);
            setRegistrationDate(individual, row, allErrorMsgs);
            LocationCreator locationCreator = new LocationCreator();
            individual.setRegistrationLocation(locationCreator.getGeoLocation(row, SubjectHeadersCreator.registrationLocation, allErrorMsgs));

            AddressLevelTypes registrationLocationTypes = subjectTypeService.getRegistrableLocationTypes(subjectType);
            individual.setAddressLevel(addressLevelCreator.findAddressLevel(row, registrationLocationTypes));

            if (individual.getSubjectType().getType().equals(Subject.Person)) setGender(individual, row);
            FormMapping formMapping = formMappingRepository.getRegistrationFormMapping(subjectType);
            individual.setVoided(false);
            individual.assignUUIDIfRequired();
            if (formMapping == null) {
                throw new Exception(String.format("No form found for the subject type %s", subjectType.getName()));
            }
            Individual savedIndividual;
            if (skipRuleExecution()) {
                individual.setObservations(observationCreator.getObservations(row, subjectHeadersCreator, allErrorMsgs, FormType.IndividualProfile, individual.getObservations()));
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
            String profilePicUrl = row.get(SubjectHeadersCreator.profilePicture);
            if(!StringUtils.isEmpty(profilePicUrl) && subjectType.isAllowProfilePicture()) {
                individual.setProfilePicture(s3Service
                        .uploadProfilePic(profilePicUrl, null));
            } else if(!StringUtils.isEmpty(profilePicUrl)) {
                errorMsgs.add(String.format("Not allowed to set '%s'", SubjectHeadersCreator.profilePicture));
            }
        } catch (Exception e) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeadersCreator.profilePicture));
        }
    }

    private Individual getOrCreateIndividual(Row row) {
        String id = row.get(SubjectHeadersCreator.id);
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
            String dob = row.get(SubjectHeadersCreator.dateOfBirth);
            if (dob != null && !dob.trim().isEmpty())
                individual.setDateOfBirth(LocalDate.parse(dob));
        } catch (Exception ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeadersCreator.dateOfBirth));
        }
    }

    private void setRegistrationDate(Individual individual, Row row, List<String> errorMsgs) {
        try {
            String registrationDate = row.get(SubjectHeadersCreator.registrationDate);
            individual.setRegistrationDate(registrationDate != null && !registrationDate.trim().isEmpty() ? LocalDate.parse(registrationDate) : LocalDate.now());
        } catch (Exception ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeadersCreator.registrationDate));
        }
    }

    private void setGender(Individual individual, Row row) throws Exception {
        try {
            String genderName = row.get(SubjectHeadersCreator.gender);
            Gender gender = genderRepository.findByNameIgnoreCase(genderName);
            if (gender == null) {
                throw new Exception(String.format("Invalid '%s' - '%s'", SubjectHeadersCreator.gender, genderName));
            }
            individual.setGender(gender);
        } catch (Exception ex) {
            throw new Exception(String.format("Invalid '%s'", SubjectHeadersCreator.gender));
        }
    }
}

package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.GenderRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.importer.batch.csv.creator.AddressLevelCreator;
import org.avni.server.importer.batch.csv.creator.LocationCreator;
import org.avni.server.importer.batch.csv.creator.ObservationCreator;
import org.avni.server.importer.batch.csv.creator.SubjectTypeCreator;
import org.avni.server.importer.batch.csv.writer.header.SubjectHeadersCreator;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
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
    private final ObservationCreator observationCreator;
    private final IndividualService individualService;
    private final S3Service s3Service;
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
                         ObservationCreator observationCreator, IndividualService individualService,
                         S3Service s3Service,
                         OrganisationConfigService organisationConfigService,
                         AddressLevelCreator addressLevelCreator, SubjectMigrationService subjectMigrationService, SubjectTypeService subjectTypeService, SubjectHeadersCreator subjectHeadersCreator) {
        super(organisationConfigService);
        this.individualRepository = individualRepository;
        this.genderRepository = genderRepository;
        this.subjectTypeCreator = subjectTypeCreator;
        this.formMappingRepository = formMappingRepository;
        this.observationCreator = observationCreator;
        this.individualService = individualService;
        this.addressLevelCreator = addressLevelCreator;
        this.subjectMigrationService = subjectMigrationService;
        this.subjectTypeService = subjectTypeService;
        this.s3Service = s3Service;
        this.subjectHeadersCreator = subjectHeadersCreator;
    }

    @Override
    public void write(Chunk<? extends Row> chunk) throws ValidationException, InvalidConfigurationException {
        List<? extends Row> rows = chunk.getItems();
        if (!CollectionUtils.isEmpty(rows)) {
            String subjectType = rows.get(0).get(SubjectHeadersCreator.subjectTypeHeader);
            FormMapping formMapping = formMappingRepository.getRegistrationFormMapping(subjectTypeCreator.getSubjectType(subjectType, SubjectHeadersCreator.subjectTypeHeader));
            TxnDataHeaderValidator.validateHeaders(rows.get(0).getHeaders(), formMapping, subjectHeadersCreator);
            for (Row row : rows) write(row);
        }
    }

    private void write(Row row) throws ValidationException {
        Individual individual = getOrCreateIndividual(row);
        AddressLevel oldAddressLevel = individual.getAddressLevel();
        ObservationCollection oldObservations = individual.getObservations();
        List<String> allErrorMsgs = new ArrayList<>();

        SubjectType subjectType = setSubjectType(row, individual, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

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

        if (individual.getSubjectType().getType().equals(Subject.Person))
            setGender(individual, row, allErrorMsgs);
        FormMapping formMapping = formMappingRepository.getRegistrationFormMapping(subjectType);
        individual.setVoided(false);
        individual.assignUUIDIfRequired();
        if (formMapping == null) {
            throw new RuntimeException(String.format("No form found for the subject type %s", subjectType.getName()));
        }
        ObservationCollection observations = observationCreator.getObservations(row, subjectHeadersCreator, allErrorMsgs, FormType.IndividualProfile, individual.getObservations(), formMapping);
        individual.setObservations(observations);
        Individual savedIndividual = individualService.save(individual);
        if (oldAddressLevel != null) {
            subjectMigrationService.markSubjectMigrationIfRequired(savedIndividual.getUuid(), oldAddressLevel, savedIndividual.getAddressLevel(), oldObservations, savedIndividual.getObservations(), false);
        }
    }

    private SubjectType setSubjectType(Row row, Individual individual, List<String> allErrorMsgs) {
        String subjectTypeValue = row.get(SubjectHeadersCreator.subjectTypeHeader);
        SubjectType subjectType = subjectTypeCreator.getSubjectType(subjectTypeValue, SubjectHeadersCreator.subjectTypeHeader);
        if (subjectType == null) {
            allErrorMsgs.add(String.format("Invalid '%s' %s", SubjectHeadersCreator.subjectTypeHeader, subjectTypeValue));
            return null;
        }
        individual.setSubjectType(subjectType);
        return subjectType;
    }

    private void setProfilePicture(SubjectType subjectType, Individual individual, Row row, List<String> errorMsgs) {
        try {
            String profilePicUrl = row.get(SubjectHeadersCreator.profilePicture);
            if (!StringUtils.isEmpty(profilePicUrl) && subjectType.isAllowProfilePicture()) {
                individual.setProfilePicture(s3Service
                        .uploadProfilePic(profilePicUrl, null));
            } else if (!StringUtils.isEmpty(profilePicUrl)) {
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
        if (StringUtils.hasText(externalId)) {
            individual.setLegacyId(externalId);
        }
        individual.setVoided(false);
        individual.assignUUIDIfRequired();
        return individual;
    }

    private void setDateOfBirth(Individual individual, Row row, List<String> errorMsgs) {
        try {
            String dob = row.get(SubjectHeadersCreator.dateOfBirth);
            if (dob != null && !dob.trim().isEmpty())
                individual.setDateOfBirth(DateTimeUtil.parseFlexibleDate(dob));
        } catch (RuntimeException ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeadersCreator.dateOfBirth));
        }
    }

    private void setRegistrationDate(Individual individual, Row row, List<String> errorMsgs) {
        try {
            String registrationDate = row.get(SubjectHeadersCreator.registrationDate);
            if (registrationDate == null || registrationDate.trim().isEmpty()) {
                errorMsgs.add(String.format("Value required for mandatory field: '%s'", SubjectHeadersCreator.registrationDate));
                return;
            }
            LocalDate providedDate = DateTimeUtil.parseFlexibleDate(registrationDate);
            if (providedDate.isAfter(LocalDate.now())) {
                errorMsgs.add(String.format("'%s' %s is in future", SubjectHeadersCreator.registrationDate, registrationDate));
                return;
            }
            individual.setRegistrationDate(providedDate);
        } catch (IllegalArgumentException ex) {
            errorMsgs.add(String.format("Invalid '%s'", SubjectHeadersCreator.registrationDate));
        }
    }

    private void setGender(Individual individual, Row row, List<String> allErrorMsgs) {
        String genderName = row.get(SubjectHeadersCreator.gender);
        if (!StringUtils.hasText(genderName)) {
            allErrorMsgs.add(String.format("Value required for mandatory field: '%s'", SubjectHeadersCreator.gender));
            return;
        }
        Gender gender = genderRepository.findByNameIgnoreCase(genderName);
        if (gender == null) {
            allErrorMsgs.add(String.format("Invalid '%s' %s", SubjectHeadersCreator.gender, genderName));
            return;
        }
        individual.setGender(gender);
    }
}

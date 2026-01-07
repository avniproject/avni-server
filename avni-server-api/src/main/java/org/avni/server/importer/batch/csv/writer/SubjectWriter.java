package org.avni.server.importer.batch.csv.writer;

import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.application.Subject;
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
import org.avni.server.util.ValidationUtil;
import org.joda.time.LocalDate;
import org.springframework.batch.item.Chunk;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Component
public class SubjectWriter extends EntityWriter {
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

    public void write(Chunk<? extends Row> chunk, String type, String locationHierarchy) throws Exception {
        List<? extends Row> rows = chunk.getItems();
        if (!CollectionUtils.isEmpty(rows)) {
            for (Row row : rows) write(row, type, locationHierarchy);
        }
    }

    private void write(Row row, String type, String locationHierarchy) throws Exception {
        List<String> allErrorMsgs = new ArrayList<>();
        String id = row.get(SubjectHeadersCreator.id);
        if (!(id == null || id.trim().isEmpty())) {
            if (individualRepository.findByLegacyIdOrUuid(id) != null) {
                allErrorMsgs.add(String.format("Entry with id from previous system, %s already present in Avni", id));
                ValidationUtil.handleErrors(allErrorMsgs);
            }
        }
        Individual individual = createNewIndividual(id);
        AddressLevel oldAddressLevel = individual.getAddressLevel();
        ObservationCollection oldObservations = individual.getObservations();

        SubjectType subjectType = setSubjectType(row, individual, allErrorMsgs, type);
        ValidationUtil.handleErrors(allErrorMsgs);

        FormMapping formMapping = formMappingRepository.getRegistrationFormMapping(subjectType);
        if (formMapping == null) {
            throw new RuntimeException(String.format("No form found for the subject type %s", subjectType.getName()));
        }
        TxnDataHeaderValidator.validateHeaders(row.getHeaders(), formMapping, subjectHeadersCreator, locationHierarchy, allErrorMsgs);
        ValidationUtil.handleErrors(allErrorMsgs);

        setFirstName(row, individual, allErrorMsgs);
        if (subjectType.isAllowMiddleName())
            individual.setMiddleName(row.get(SubjectHeadersCreator.middleName));
        individual.setLastName(row.get(SubjectHeadersCreator.lastName));
        setProfilePicture(subjectType, individual, row, allErrorMsgs);
        if (subjectType.isPerson())
            setDateOfBirth(individual, row, allErrorMsgs);
        Boolean dobVerified = row.getBool(SubjectHeadersCreator.dobVerified);
        individual.setDateOfBirthVerified(dobVerified != null ? dobVerified : false);
        setRegistrationDate(individual, row, allErrorMsgs);

        LocationCreator locationCreator = new LocationCreator();
        individual.setRegistrationLocation(locationCreator.getGeoLocation(row, SubjectHeadersCreator.registrationLocation, allErrorMsgs));

        AddressLevelTypes registrationLocationTypes = subjectTypeService.getRegistrableLocationTypes(subjectType);
        setAddressLevel(row, individual, registrationLocationTypes, allErrorMsgs);

        if (individual.getSubjectType().getType().equals(Subject.Person))
            setGender(individual, row, allErrorMsgs);
        individual.setVoided(false);
        individual.assignUUIDIfRequired();
        ObservationCollection observations = observationCreator.getObservations(row, subjectHeadersCreator, allErrorMsgs, FormType.IndividualProfile, individual.getObservations(), formMapping);
        ValidationUtil.handleErrors(allErrorMsgs);
        individual.setObservations(observations);
        Individual savedIndividual = individualService.save(individual);
        if (oldAddressLevel != null) {
            subjectMigrationService.markSubjectMigrationIfRequired(savedIndividual.getUuid(), oldAddressLevel, savedIndividual.getAddressLevel(), oldObservations, savedIndividual.getObservations(), false);
        }
    }

    private void setAddressLevel(Row row, Individual individual, AddressLevelTypes registrationLocationTypes, List<String> allErrorMsgs) {
        AddressLevel addressLevel = addressLevelCreator.findAddressLevel(row, registrationLocationTypes);
        if (addressLevel == null) {
            allErrorMsgs.add("Subject registration location provided not found.");
            return;
        }
        individual.setAddressLevel(addressLevel);
    }

    private static void setFirstName(Row row, Individual individual, List<String> allErrorMsgs) {
        String firstName = row.get(SubjectHeadersCreator.firstName);
        if (!StringUtils.hasText(firstName)) {
            allErrorMsgs.add(String.format("Value required for mandatory field: '%s'", SubjectHeadersCreator.firstName));
            return;
        }
        individual.setFirstName(firstName);
    }

    private SubjectType setSubjectType(Row row, Individual individual, List<String> allErrorMsgs, String type) {
        String subjectTypeChosen = type.split("---")[1];
        String subjectTypeValue = row.get(SubjectHeadersCreator.subjectTypeHeader);
        SubjectType subjectType = subjectTypeCreator.getSubjectType(subjectTypeValue, SubjectHeadersCreator.subjectTypeHeader);
        if (subjectType == null) {
            allErrorMsgs.add(String.format("Invalid or missing '%s' %s", SubjectHeadersCreator.subjectTypeHeader, subjectTypeValue));
            return null;
        } else if (!subjectTypeChosen.equalsIgnoreCase(subjectType.getName())) {
            allErrorMsgs.add("Upload file type chosen to upload does not match with the subject type provided in the file");
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
        LocalDate date = row.ensureDateIsPresentAndNotInFuture(SubjectHeadersCreator.dateOfBirth, errorMsgs);
        if (date == null) {
            return;
        }
        individual.setDateOfBirth(date);
    }

    private void setRegistrationDate(Individual individual, Row row, List<String> errorMsgs) {
        LocalDate date = row.ensureDateIsPresentAndNotInFuture(SubjectHeadersCreator.registrationDate, errorMsgs);
        if (date == null) {
            return;
        }
        individual.setRegistrationDate(date);
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

package org.avni.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.messaging.domain.EntityType;
import org.avni.messaging.service.PhoneNumberNotAvailableOrIncorrectException;
import org.avni.server.application.*;
import org.avni.server.common.Messageable;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.individualRelationship.IndividualRelation;
import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.avni.server.domain.observation.PhoneNumber;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.S;
import org.avni.server.web.request.*;
import org.avni.server.web.request.api.RequestUtils;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import static org.avni.messaging.domain.Constants.NO_OF_DIGITS_IN_INDIAN_MOBILE_NO;

import javax.persistence.EntityNotFoundException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Service
public class IndividualService implements ScopeAwareService {
    public static final String PHONE_NUMBER_FOR_SUBJECT_ID = "phoneNumberForSubjectId";
    private final IndividualRepository individualRepository;
    private final ObservationService observationService;
    private final GroupSubjectRepository groupSubjectRepository;
    private final ConceptRepository conceptRepository;
    private final GroupRoleRepository groupRoleRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final AddressLevelService addressLevelService;
    private final ConceptService conceptService;
    private final ObjectMapper objectMapper;
    private final AccessControlService accessControlService;

    @Autowired
    public IndividualService(IndividualRepository individualRepository, ObservationService observationService, GroupSubjectRepository groupSubjectRepository, ConceptRepository conceptRepository, GroupRoleRepository groupRoleRepository, SubjectTypeRepository subjectTypeRepository, AddressLevelService addressLevelService, ConceptService conceptService, AccessControlService accessControlService) {
        this.individualRepository = individualRepository;
        this.observationService = observationService;
        this.groupSubjectRepository = groupSubjectRepository;
        this.conceptRepository = conceptRepository;
        this.groupRoleRepository = groupRoleRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.addressLevelService = addressLevelService;
        this.conceptService = conceptService;
        this.accessControlService = accessControlService;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public Individual findByUuid(String uuid) {
        return individualRepository.findByUuid(uuid);
    }

    public Individual findById(Long id) {
        return individualRepository.findEntity(id);
    }

    public IndividualContract getSubjectEncounters(String individualUuid) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null) {
            return null;
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, individual);
        Set<EncounterContract> encountersContractList = constructEncounters(individual.nonVoidedEncounters());
        IndividualContract individualContract = new IndividualContract();
        individualContract.setEncounters(encountersContractList);
        return individualContract;
    }

    public IndividualContract getSubjectProgramEnrollment(String individualUuid) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null) {
            return null;
        }
        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, individual);

        List<EnrolmentContract> enrolmentContractList = constructEnrolmentsMetadata(individual);
        IndividualContract individualContract = new IndividualContract();
        individualContract.setUuid(individual.getUuid());
        individualContract.setId(individual.getId());
        individualContract.setEnrolments(enrolmentContractList);
        individualContract.setVoided(individual.isVoided());
        return individualContract;
    }

    public IndividualContract getSubjectInfo(String individualUuid) {
        Individual individual = individualRepository.findByUuid(individualUuid);
        IndividualContract individualContract = new IndividualContract();
        if (individual == null) {
            return null;
        }

        List<ObservationContract> observationContractsList = observationService.constructObservations(individual.getObservations());
        List<RelationshipContract> relationshipContractList = constructRelationships(individual);
        List<EnrolmentContract> enrolmentContractList = constructEnrolments(individual);
        List<GroupSubject> groupSubjects = groupSubjectRepository.findAllByMemberSubjectAndGroupRoleIsVoidedFalseAndIsVoidedFalse(individual);
        List<GroupRole> groupRoles = groupRoleRepository.findByGroupSubjectType_IdAndIsVoidedFalse(individual.getSubjectType().getId());
        individualContract.setId(individual.getId());
        individualContract.setSubjectType(SubjectTypeContract.fromSubjectType(individual.getSubjectType()));
        individualContract.setObservations(observationContractsList);
        individualContract.setRelationships(relationshipContractList);
        individualContract.setEnrolments(enrolmentContractList);
        individualContract.setUuid(individual.getUuid());
        individualContract.setFirstName(individual.getFirstName());
        individualContract.setMiddleName(individual.getMiddleName());
        individualContract.setLastName(individual.getLastName());
        if (null != individual.getProfilePicture()
            && individual.getSubjectType().isAllowProfilePicture())
            individualContract.setProfilePicture(individual.getProfilePicture());
        if (null != individual.getDateOfBirth())
            individualContract.setDateOfBirth(individual.getDateOfBirth());
        if (null != individual.getGender()) {
            individualContract.setGender(individual.getGender().getName());
            individualContract.setGenderUUID(individual.getGender().getUuid());
        }
        if (groupSubjects != null) {
            individualContract.setMemberships(groupSubjects.stream().map(GroupSubjectContract::fromEntity).collect(Collectors.toList()));
        }
        if (groupRoles != null) {
            individualContract.setRoles(groupRoles.stream().map(GroupRoleContract::fromEntity).collect(Collectors.toList()));
        }
        individualContract.setRegistrationDate(individual.getRegistrationDate());
        individualContract.setVoided(individual.isVoided());
        AddressLevel addressLevel = individual.getAddressLevel();
        if (addressLevel != null) {
            individualContract.setAddressLevel(addressLevel.getTitle());
            individualContract.setAddressLevelLineage(addressLevelService.getTitleLineage(addressLevel));
            individualContract.setAddressLevelUUID(addressLevel.getUuid());
            individualContract.setAddressLevelTypeName(addressLevel.getType().getName());
            individualContract.setAddressLevelTypeId(addressLevel.getType().getId());
        }
        return individualContract;
    }

    public List<EnrolmentContract> constructEnrolmentsMetadata(Individual individual) {
        return individual.getProgramEnrolments().stream().filter(x -> !x.isVoided()).map(programEnrolment -> {
            accessControlService.checkProgramPrivilege(PrivilegeType.ViewEnrolmentDetails, programEnrolment);
            EnrolmentContract enrolmentContract = new EnrolmentContract();
            enrolmentContract.setUuid(programEnrolment.getUuid());
            enrolmentContract.setId(programEnrolment.getId());
            enrolmentContract.setProgramUuid(programEnrolment.getProgram().getUuid());
            enrolmentContract.setOperationalProgramName(programEnrolment.getProgram().getOperationalProgramName());
            enrolmentContract.setEnrolmentDateTime(programEnrolment.getEnrolmentDateTime());
            enrolmentContract.setProgramExitDateTime(programEnrolment.getProgramExitDateTime());
            enrolmentContract.setProgramEncounters(constructProgramEncounters(programEnrolment.nonVoidedEncounters()));
            enrolmentContract.setVoided(programEnrolment.isVoided());
            enrolmentContract.setProgramName(programEnrolment.getProgram().getName());
            List<ObservationContract> observationContractsList = observationService.constructObservations(programEnrolment.getObservations());
            enrolmentContract.setObservations(observationContractsList);
            if (programEnrolment.getProgramExitObservations() != null) {
                enrolmentContract.setExitObservations(observationService.constructObservations(programEnrolment.getProgramExitObservations()));
            }
            return enrolmentContract;
        }).collect(Collectors.toList());
    }

    public Set<EncounterContract> constructEncounters(Stream<Encounter> encounters) {
        return encounters.map(encounter -> {
            accessControlService.checkEncounterPrivilege(PrivilegeType.ViewVisit, encounter);
            EncounterContract encounterContract = new EncounterContract();
            EntityTypeContract entityTypeContract = new EntityTypeContract();
            entityTypeContract.setUuid(encounter.getEncounterType().getUuid());
            entityTypeContract.setName(encounter.getEncounterType().getOperationalEncounterTypeName());
            encounterContract.setId(encounter.getId());
            encounterContract.setUuid(encounter.getUuid());
            encounterContract.setName(encounter.getName());
            encounterContract.setEncounterType(entityTypeContract);
            encounterContract.setEncounterDateTime(encounter.getEncounterDateTime());
            encounterContract.setEarliestVisitDateTime(encounter.getEarliestVisitDateTime());
            encounterContract.setMaxVisitDateTime(encounter.getMaxVisitDateTime());
            encounterContract.setCancelDateTime(encounter.getCancelDateTime());
            encounterContract.setVoided(encounter.isVoided());
            return encounterContract;
        }).collect(Collectors.toSet());
    }


    public Set<ProgramEncounterContract> constructProgramEncounters(Stream<ProgramEncounter> programEncounters) {
        return programEncounters.map(programEncounter -> {
            ProgramEncounterContract programEncountersContract = new ProgramEncounterContract();
            EntityTypeContract entityTypeContract =
                EntityTypeContract.fromEncounterType(programEncounter.getEncounterType());
            programEncountersContract.setUuid(programEncounter.getUuid());
            programEncountersContract.setId(programEncounter.getId());
            programEncountersContract.setName(programEncounter.getName());
            programEncountersContract.setEncounterType(entityTypeContract);
            programEncountersContract.setEncounterDateTime(programEncounter.getEncounterDateTime());
            programEncountersContract.setCancelDateTime(programEncounter.getCancelDateTime());
            programEncountersContract.setEarliestVisitDateTime(programEncounter.getEarliestVisitDateTime());
            programEncountersContract.setMaxVisitDateTime(programEncounter.getMaxVisitDateTime());
            programEncountersContract.setVoided(programEncounter.isVoided());
            return programEncountersContract;
        }).collect(Collectors.toSet());
    }

    public List<EnrolmentContract> constructEnrolments(Individual individual) {

        return individual.getProgramEnrolments().stream().map(programEnrolment -> {
            EnrolmentContract enrolmentContract = new EnrolmentContract();
            enrolmentContract.setId(programEnrolment.getId());
            enrolmentContract.setUuid(programEnrolment.getUuid());
            enrolmentContract.setOperationalProgramName(programEnrolment.getProgram().getOperationalProgramName());
            enrolmentContract.setEnrolmentDateTime(programEnrolment.getEnrolmentDateTime());
            enrolmentContract.setProgramExitDateTime(programEnrolment.getProgramExitDateTime());
            enrolmentContract.setVoided(programEnrolment.isVoided());
            return enrolmentContract;
        }).collect(Collectors.toList());
    }

    public List<RelationshipContract> constructRelationships(Individual individual) {
        List<RelationshipContract> relationshipContractFromSelfToOthers = individual.getRelationshipsFromSelfToOthers().stream().filter(individualRelationship -> !individualRelationship.isVoided()).map(individualRelationship -> {
            Individual individualB = individualRelationship.getIndividualB();
            IndividualRelation individualRelation = individualRelationship.getRelationship().getIndividualBIsToA();
            return constructCommonRelationship(individualRelationship, individualB, individualRelation, individualRelationship.getRelationship().getIndividualAIsToB());
        }).collect(Collectors.toList());

        List<RelationshipContract> relationshipContractFromOthersToSelf = individual.getRelationshipsFromOthersToSelf().stream().filter(individualRelationship -> !individualRelationship.isVoided()).map(individualRelationship -> {
            Individual individualA = individualRelationship.getIndividuala();
            IndividualRelation individualRelation = individualRelationship.getRelationship().getIndividualAIsToB();
            return constructCommonRelationship(individualRelationship, individualA, individualRelation, individualRelationship.getRelationship().getIndividualBIsToA());
        }).collect(Collectors.toList());
        relationshipContractFromSelfToOthers.addAll(relationshipContractFromOthersToSelf);
        return relationshipContractFromSelfToOthers;
    }

    private RelationshipContract constructCommonRelationship(IndividualRelationship individualRelationship, Individual individual, IndividualRelation individualRelation, IndividualRelation individualAIsToBRelation) {
        RelationshipContract relationshipContract = new RelationshipContract();
        IndividualContract individualBContract = new IndividualContract();
        individualBContract.setUuid(individual.getUuid());
        individualBContract.setFirstName(individual.getFirstName());
        individualBContract.setMiddleName(individual.getMiddleName());
        individualBContract.setLastName(individual.getLastName());
        if (individual.getSubjectType().isAllowProfilePicture()) {
            individualBContract.setProfilePicture(individual.getProfilePicture());
        }
        individualBContract.setDateOfBirth(individual.getDateOfBirth());
        individualBContract.setSubjectType(SubjectTypeContract.fromSubjectType(individual.getSubjectType()));
        relationshipContract.setIndividualB(individualBContract);

        IndividualRelationshipTypeContract individualRelationshipTypeContract = new IndividualRelationshipTypeContract();
        individualRelationshipTypeContract.setUuid(individualRelationship.getRelationship().getUuid());
        individualRelationshipTypeContract.getIndividualBIsToARelation().setName(individualRelation.getName());
        individualRelationshipTypeContract.getIndividualAIsToBRelation().setName(individualAIsToBRelation.getName());
        relationshipContract.setRelationshipType(individualRelationshipTypeContract);
        relationshipContract.setUuid(individualRelationship.getUuid());
        relationshipContract.setEnterDateTime(individualRelationship.getEnterDateTime());
        relationshipContract.setExitDateTime(individualRelationship.getExitDateTime());
        relationshipContract.setVoided(individualRelationship.isVoided());
        if (individualRelationship.getExitObservations() != null) {
            relationshipContract.setExitObservations(observationService.constructObservations(individualRelationship.getExitObservations()));
        }
        if (null != individualRelationship && null != individualRelationship.getId()) {
            relationshipContract.setId(individualRelationship.getId());
        }
        if (null != individualRelationship && null != individualRelationship.getId())
            individualRelationshipTypeContract.setId(individualRelationship.getRelationship().getId());
        return relationshipContract;
    }

    public GroupSubjectContractWeb createGroupSubjectContractWeb(String uuid, Individual member, GroupRole groupRole) {
        GroupSubjectContractWeb groupSubjectContractWeb = new GroupSubjectContractWeb();
        groupSubjectContractWeb.setUuid(uuid);
        groupSubjectContractWeb.setMember(createIndividualContractWeb(member));
        groupSubjectContractWeb.setRole(GroupRoleContract.fromEntity(groupRole));
        groupSubjectContractWeb.setEncounterMetadata(createEncounterMetadataContract(member));
        return groupSubjectContractWeb;
    }

    private IndividualContract createIndividualContractWeb(Individual individual) {
        IndividualContract individualContractWeb = new IndividualContract();
        individualContractWeb.setId(individual.getId());
        individualContractWeb.setUuid(individual.getUuid());
        individualContractWeb.setFirstName(individual.getFirstName());
        individualContractWeb.setMiddleName(individual.getMiddleName());
        individualContractWeb.setLastName(individual.getLastName());
        if (individual.getSubjectType().isAllowProfilePicture()) {
            individualContractWeb.setProfilePicture(individual.getProfilePicture());
        }
        individualContractWeb.setDateOfBirth(individual.getDateOfBirth());
        individualContractWeb.setSubjectType(SubjectTypeContract.fromSubjectType(individual.getSubjectType()));
        if (individual.getSubjectType().getType().equals(Subject.Person)) {
            individualContractWeb.setGender(individual.getGender().getName());
        }

        return individualContractWeb;
    }

    private EncounterMetadataContract createEncounterMetadataContract(Individual individual) {
        EncounterMetadataContract encounterMetadataContract = new EncounterMetadataContract();

        Long scheduledEncounters = individual.scheduledEncounters().count();
        Long overdueEncounters = individual.scheduledEncounters().filter(encounter -> encounter.getMaxVisitDateTime().isBeforeNow()).count();

        encounterMetadataContract.setDueEncounters(scheduledEncounters - overdueEncounters);
        encounterMetadataContract.setOverdueEncounters(overdueEncounters);
        return encounterMetadataContract;
    }

    @Messageable(EntityType.Subject)
    public Individual voidSubject(Individual individual) {
        assertNoUnVoidedEncounters(individual);
        assertNoUnVoidedEnrolments(individual);
        individual.setVoided(true);
        return individualRepository.save(individual);
    }

    private void assertNoUnVoidedEnrolments(Individual individual) {
        long nonVoidedProgramEnrolments = individual.getProgramEnrolments()
            .stream()
            .filter(pe -> !pe.isVoided())
            .count();
        if (nonVoidedProgramEnrolments != 0) {
            throw new BadRequestError(String.format("There are non deleted program enrolments for the %s %s", individual.getSubjectType().getOperationalSubjectTypeName(), individual.getFirstName()));
        }
    }

    private void assertNoUnVoidedEncounters(Individual individual) {
        long nonVoidedEncounterCount = individual.nonVoidedEncounters().count();
        if (nonVoidedEncounterCount != 0) {
            throw new BadRequestError(String.format("There are non deleted general encounters for the %s %s", individual.getSubjectType().getOperationalSubjectTypeName(), individual.getFirstName()));
        }
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUUID) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        User user = UserContextHolder.getUserContext().getUser();
        return subjectType != null && isChangedBySubjectTypeRegistrationLocationType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncEntityName.Individual);
    }

    @Override
    public OperatingIndividualScopeAwareRepository repository() {
        return individualRepository;
    }

    public Object getObservationValueForUpload(FormElement formElement, String answerValue) {
        Concept concept = formElement.getConcept();
        SubjectType subjectType = subjectTypeRepository.findByUuid(concept.getKeyValues().get(KeyType.subjectTypeUUID).getValue().toString());
        if (formElement.getType().equals(FormElementType.MultiSelect.name())) {
            String[] providedAnswers = S.splitMultiSelectAnswer(answerValue);
            return Stream.of(providedAnswers)
                .map(answer -> individualRepository.findByLegacyIdOrUuidAndSubjectType(answer, subjectType).getUuid())
                .collect(Collectors.toList());
        } else {
            return individualRepository.findByLegacyIdOrUuidAndSubjectType(answerValue, subjectType).getUuid();
        }
    }

    @Messageable(EntityType.Subject)
    public Individual save(Individual individual) {
        individual.addConceptSyncAttributeValues(individual.getSubjectType(), individual.getObservations());
        return individualRepository.save(individual);
    }

    public String findPhoneNumber(long subjectId) {
        Individual individual = this.getIndividual(subjectId);
        return findPhoneNumber(individual);
    }

    public Individual getIndividual(long subjectId) {
        return individualRepository.findEntity(subjectId);
    }

    public String findPhoneNumber(Individual individual) {
        assert individual != null;

        Optional<Concept> phoneNumberConcept = conceptRepository.findAllByDataType("PhoneNumber").stream().findFirst();
        if (phoneNumberConcept.isPresent()) {
            Optional<String> phoneNumber = individual.getObservations().entrySet().stream().filter(entrySet ->
                    Objects.equals(entrySet.getKey(), phoneNumberConcept.get().getUuid()))
                .map(phoneNumberEntry -> objectMapper.convertValue(phoneNumberEntry.getValue(), PhoneNumber.class).getPhoneNumber()).findFirst();
            if (phoneNumber.isPresent()) {
                return phoneNumber.get();
            }
        }
        Optional<Concept> phoneNumberTextConcept = conceptService.findContactNumberConcept();
        if (phoneNumberTextConcept.isPresent()) {
            Optional<String> phoneNumber = individual.getObservations().entrySet().stream().filter(entrySet ->
                    Objects.equals(entrySet.getKey(), phoneNumberTextConcept.get().getUuid()))
                .map(stringObjectEntry -> (String) (stringObjectEntry.getValue())).findFirst();
            if (phoneNumber.isPresent()) {
                return phoneNumber.get();
            }
        }
        return null;
    }

    @Cacheable(value = PHONE_NUMBER_FOR_SUBJECT_ID)
    public String fetchIndividualPhoneNumber(String subjectId) throws PhoneNumberNotAvailableOrIncorrectException {
        Individual individual = getIndividual(subjectId);
        String phoneNumber = findPhoneNumber(individual);
        if (StringUtils.hasText(phoneNumber)) {
            return phoneNumber;
        } else {
            throw new PhoneNumberNotAvailableOrIncorrectException();
        }
    }

    public Individual getIndividual(String subjectId) {
        Individual individual = null;
        if (RequestUtils.isValidUUID(subjectId)) {
            individual = individualRepository.findByUuid(subjectId);
        } else {
            individual = individualRepository.findOne(Long.parseLong(subjectId));
        }
        if (individual == null) {
            throw new EntityNotFoundException("Subject not found with id / uuid: " + subjectId);
        }
        return individual;
    }

    public Optional<Individual> findByPhoneNumber(String phoneNumber) {
        Optional<Concept> phoneNumberConcept = conceptRepository.findAllByDataType("PhoneNumber").stream().findFirst();
        if (!phoneNumberConcept.isPresent()) {
            phoneNumberConcept = conceptService.findContactNumberConcept();
        }
        phoneNumber = phoneNumber.substring(phoneNumber.length() - NO_OF_DIGITS_IN_INDIAN_MOBILE_NO);
        return phoneNumberConcept.isPresent()
            ? individualRepository.findByConceptWithMatchingPattern(phoneNumberConcept.get(), "%" + phoneNumber)
            : Optional.empty();
    }
}

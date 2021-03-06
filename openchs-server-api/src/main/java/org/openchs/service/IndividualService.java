package org.openchs.service;

import org.openchs.application.Subject;
import org.openchs.dao.*;
import org.openchs.domain.*;
import org.openchs.domain.individualRelationship.IndividualRelation;
import org.openchs.domain.individualRelationship.IndividualRelationship;
import org.openchs.util.BadRequestError;
import org.openchs.web.request.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.springframework.data.jpa.domain.Specifications.where;


@Service
public class IndividualService {
    private final Logger logger;
    private final IndividualRepository individualRepository;
    private final ConceptRepository conceptRepository;
    private final ProjectionFactory projectionFactory;
    private final EncounterRepository encounterRepository;
    private final ObservationService observationService;
    private final GroupSubjectRepository groupSubjectRepository;
    private final GroupRoleRepository groupRoleRepository;

    @Autowired
    public IndividualService(ConceptRepository conceptRepository, IndividualRepository individualRepository, ProjectionFactory projectionFactory, EncounterRepository encounterRepository, ObservationService observationService, GroupSubjectRepository groupSubjectRepository, GroupRoleRepository groupRoleRepository) {
        this.projectionFactory = projectionFactory;
        logger = LoggerFactory.getLogger(this.getClass());
        this.conceptRepository = conceptRepository;
        this.individualRepository = individualRepository;
        this.encounterRepository = encounterRepository;
        this.observationService = observationService;
        this.groupSubjectRepository = groupSubjectRepository;
        this.groupRoleRepository = groupRoleRepository;
    }

    public  IndividualContract getSubjectEncounters(String individualUuid){
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null)  {
            return null;
        }
        Set<EncounterContract> encountersContractList = constructEncounters(individual.nonVoidedEncounters());
        IndividualContract individualContract = new IndividualContract();
        individualContract.setEncounters(encountersContractList);
        return individualContract;
    }

    public  IndividualContract getSubjectProgramEnrollment(String individualUuid){
        Individual individual = individualRepository.findByUuid(individualUuid);
        if (individual == null)  {
            return null;
        }
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
        if (individual == null)  {
            return null;
        }

        List<ObservationContract> observationContractsList = observationService.constructObservations(individual.getObservations());
        List<RelationshipContract> relationshipContractList = constructRelationships(individual);
        List<EnrolmentContract> enrolmentContractList = constructEnrolments(individual);
        List<GroupSubject> groupSubjects = groupSubjectRepository.findAllByMemberSubject(individual);
        List<GroupRole> groupRoles = groupRoleRepository.findByGroupSubjectType_IdAndIsVoidedFalse(individual.getSubjectType().getId());
        individualContract.setId(individual.getId());
        individualContract.setSubjectType(constructSubjectType(individual.getSubjectType()));
        individualContract.setObservations(observationContractsList);
        individualContract.setRelationships(relationshipContractList);
        individualContract.setEnrolments(enrolmentContractList);
        individualContract.setUuid(individual.getUuid());
        individualContract.setFirstName(individual.getFirstName());
        individualContract.setLastName(individual.getLastName());
        if(null!=individual.getDateOfBirth())
            individualContract.setDateOfBirth(individual.getDateOfBirth());
        if(null!=individual.getGender()) {
            individualContract.setGender(individual.getGender().getName());
            individualContract.setGenderUUID(individual.getGender().getUuid());
        }
        if (groupSubjects != null) {
            individualContract.setMemberships(groupSubjects.stream().map(GroupSubjectContract::fromEntity).collect(Collectors.toList()));
        }
        if (groupRoles != null) {
            individualContract.setRoles(groupRoles.stream().map(GroupRoleContract::fromEntity).collect(Collectors.toList()));
        }
        individualContract.setAddressLevelTypeName(individual.getAddressLevel().getType().getName());
        individualContract.setAddressLevelTypeId(individual.getAddressLevel().getType().getId());
        individualContract.setRegistrationDate(individual.getRegistrationDate());
        individualContract.setAddressLevel(individual.getAddressLevel().getTitle());
        individualContract.setAddressLevelLineage(individual.getAddressLevel().getTitleLineage());
        individualContract.setAddressLevelUUID(individual.getAddressLevel().getUuid());
        individualContract.setVoided(individual.isVoided());
        return individualContract;
    }

    private SubjectTypeContract constructSubjectType(SubjectType subjectType) {
        SubjectTypeContract subjectTypeContract = new SubjectTypeContract();
        subjectTypeContract.setUuid(subjectType.getUuid());
        subjectTypeContract.setName(subjectType.getName());
        subjectTypeContract.setVoided(subjectType.isVoided());
        subjectTypeContract.setType(subjectType.getType().toString());
        subjectTypeContract.setIsGroup(subjectType.isGroup());
        subjectTypeContract.setHousehold(subjectType.isHousehold());
        return subjectTypeContract;
    }

    public List<EnrolmentContract> constructEnrolmentsMetadata(Individual individual) {
        return individual.getProgramEnrolments().stream().filter(x -> !x.isVoided()).map(programEnrolment -> {
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
            EncounterContract encounterContract = new EncounterContract();
            EncounterTypeContract encounterTypeContract = new EncounterTypeContract();
            encounterTypeContract.setUuid(encounter.getEncounterType().getUuid());
            encounterTypeContract.setName(encounter.getEncounterType().getOperationalEncounterTypeName());
            encounterContract.setId(encounter.getId());
            encounterContract.setUuid(encounter.getUuid());
            encounterContract.setName(encounter.getName());
            encounterContract.setEncounterType(encounterTypeContract);
            encounterContract.setEncounterDateTime(encounter.getEncounterDateTime());
            encounterContract.setEarliestVisitDateTime(encounter.getEarliestVisitDateTime());
            encounterContract.setMaxVisitDateTime(encounter.getMaxVisitDateTime());
            encounterContract.setCancelDateTime(encounter.getCancelDateTime());
            encounterContract.setVoided(encounter.isVoided());
            return encounterContract;
        }).collect(Collectors.toSet());
    }


    public Set <ProgramEncountersContract> constructProgramEncounters(Stream<ProgramEncounter> programEncounters) {
        return programEncounters.map(programEncounter -> {
            ProgramEncountersContract programEncountersContract = new ProgramEncountersContract();
            EncounterTypeContract encounterTypeContract =
                    EncounterTypeContract.fromEncounterType(programEncounter.getEncounterType());
            programEncountersContract.setUuid(programEncounter.getUuid());
            programEncountersContract.setId(programEncounter.getId());
            programEncountersContract.setName(programEncounter.getName());
            programEncountersContract.setEncounterType(encounterTypeContract);
            programEncountersContract.setEncounterDateTime(programEncounter.getEncounterDateTime());
            programEncountersContract.setCancelDateTime(programEncounter.getCancelDateTime());
            programEncountersContract.setEarliestVisitDateTime(programEncounter.getEarliestVisitDateTime());
            programEncountersContract.setMaxVisitDateTime(programEncounter.getMaxVisitDateTime());
            programEncountersContract.setVoided(programEncounter.isVoided());
            return  programEncountersContract;
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
            return constructCommonRelationship(individualRelationship,individualB,individualRelation,individualRelationship.getRelationship().getIndividualAIsToB());
        }).collect(Collectors.toList());

        List<RelationshipContract> relationshipContractFromOthersToSelf = individual.getRelationshipsFromOthersToSelf().stream().filter(individualRelationship -> !individualRelationship.isVoided()).map(individualRelationship -> {
            Individual individualA = individualRelationship.getIndividuala();
            IndividualRelation individualRelation = individualRelationship.getRelationship().getIndividualAIsToB();
            return constructCommonRelationship(individualRelationship,individualA,individualRelation,individualRelationship.getRelationship().getIndividualBIsToA());
        }).collect(Collectors.toList());
        relationshipContractFromSelfToOthers.addAll(relationshipContractFromOthersToSelf);
        return relationshipContractFromSelfToOthers;
    }

    private RelationshipContract constructCommonRelationship(IndividualRelationship individualRelationship,Individual individual,IndividualRelation individualRelation,IndividualRelation individualAIsToBRelation) {
        RelationshipContract relationshipContract = new RelationshipContract();
        IndividualContract individualBContract = new IndividualContract();
        individualBContract.setUuid(individual.getUuid());
        individualBContract.setFirstName(individual.getFirstName());
        individualBContract.setLastName(individual.getLastName());
        individualBContract.setDateOfBirth(individual.getDateOfBirth());
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
        if(null!=individualRelationship && null!=individualRelationship.getId()) {
            relationshipContract.setId(individualRelationship.getId());
        }
        if(null!=individualRelationship && null!=individualRelationship.getId())
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
        individualContractWeb.setLastName(individual.getLastName());
        individualContractWeb.setDateOfBirth(individual.getDateOfBirth());
        individualContractWeb.setSubjectType(SubjectTypeContract.fromSubjectType(individual.getSubjectType()));
        if (individual.getSubjectType().getType().equals(Subject.Person)) individualContractWeb.setGender(individual.getGender().getName());

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

}

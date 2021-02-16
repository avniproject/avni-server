package org.openchs.web.request.rules.constructWrappers;

import org.openchs.dao.GenderRepository;
import org.openchs.dao.IndividualRepository;
import org.openchs.dao.LocationRepository;
import org.openchs.dao.SubjectTypeRepository;
import org.openchs.domain.AddressLevel;
import org.openchs.domain.Gender;
import org.openchs.domain.Individual;
import org.openchs.domain.SubjectType;
import org.openchs.web.request.GenderContract;
import org.openchs.web.request.ObservationModelContract;
import org.openchs.web.request.SubjectTypeContract;
import org.openchs.web.request.rules.RulesContractWrapper.IndividualContractWrapper;
import org.openchs.web.request.rules.RulesContractWrapper.LowestAddressLevelContract;
import org.openchs.web.request.rules.request.IndividualRequestEntity;
import org.openchs.web.request.rules.request.ObservationRequestEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class IndividualConstructionService {
    private final GenderRepository genderRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final LocationRepository locationRepository;
    private final ObservationConstructionService observationConstructionService;
    private final IndividualRepository individualRepository;

    @Autowired
    public IndividualConstructionService(
            GenderRepository genderRepository,
            SubjectTypeRepository subjectTypeRepository,
            LocationRepository locationRepository,
            ObservationConstructionService observationConstructionService,
            IndividualRepository individualRepository) {
        this.individualRepository = individualRepository;
        this.genderRepository = genderRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.locationRepository = locationRepository;
        this.observationConstructionService = observationConstructionService;
    }


    public IndividualContractWrapper constructIndividualContract(IndividualRequestEntity individualRequestEntity){
        IndividualContractWrapper individualContract = new IndividualContractWrapper();
        individualContract.setUuid(individualRequestEntity.getUuid());
        individualContract.setFirstName(individualRequestEntity.getFirstName());
        individualContract.setLastName(individualRequestEntity.getLastName());
        individualContract.setRegistrationDate(individualRequestEntity.getRegistrationDate());
        individualContract.setDateOfBirth(individualRequestEntity.getDateOfBirth());
        if(individualRequestEntity.getGenderUUID() != null){
            Gender gender = genderRepository.findByUuid(individualRequestEntity.getGenderUUID());
            individualContract.setGender(constructGenderContract(gender));
        }
        if(individualRequestEntity.getSubjectTypeUUID() != null){
            SubjectType subjectType = subjectTypeRepository.findByUuid(individualRequestEntity.getSubjectTypeUUID());
            individualContract.setSubjectType(constructSubjectType(subjectType));
        }
        if(individualRequestEntity.getAddressLevelUUID() != null){
            AddressLevel addressLevel = locationRepository.findByUuid(individualRequestEntity.getAddressLevelUUID());
            individualContract.setLowestAddressLevel(constructAddressLevel(addressLevel));
        }
        if(individualRequestEntity.getObservations() != null){
            individualContract.setObservations(individualRequestEntity.getObservations().stream().map( x -> observationConstructionService.constructObservation(x)).collect(Collectors.toList()));
        }
        return individualContract;
    }

    private SubjectTypeContract constructSubjectType(SubjectType subjectType) {
        SubjectTypeContract subjectTypeContract = new SubjectTypeContract();
        subjectTypeContract.setName(subjectType.getName());
        subjectTypeContract.setUuid(subjectType.getUuid());
        return subjectTypeContract;
    }

    private LowestAddressLevelContract constructAddressLevel(AddressLevel addressLevel) {
        LowestAddressLevelContract lowestAddressLevelContract = new LowestAddressLevelContract();
        lowestAddressLevelContract.setName(addressLevel.getTitle());
        lowestAddressLevelContract.setAuditId(addressLevel.getAuditId());
        lowestAddressLevelContract.setUuid(addressLevel.getUuid());
        lowestAddressLevelContract.setVersion(addressLevel.getVersion());
        lowestAddressLevelContract.setOrganisationId(addressLevel.getOrganisationId());
        lowestAddressLevelContract.setTitle(addressLevel.getTitle());
        lowestAddressLevelContract.setLevel(addressLevel.getLevel());
        lowestAddressLevelContract.setParentId(addressLevel.getParentId());
        return lowestAddressLevelContract;
    }

    private GenderContract constructGenderContract(Gender gender) {
        return new GenderContract(gender.getUuid(),gender.getName());
    }


    public List<IndividualContractWrapper> findAllByLocationAndSubjectType(SubjectType subjectType, AddressLevel addressLevel) {
        List<Individual> individuals = individualRepository.findAllByAddressLevelAndSubjectTypeAndIsVoidedFalse(addressLevel, subjectType);
        return individuals.stream()
                .map(this::mapToIndividualContractWrapper)
                .collect(Collectors.toList());
    }

    private IndividualContractWrapper mapToIndividualContractWrapper(Individual individual) {
        IndividualContractWrapper individualContract = new IndividualContractWrapper();
        individualContract.setUuid(individual.getUuid());
        individualContract.setFirstName(individual.getFirstName());
        individualContract.setLastName(individual.getLastName());
        individualContract.setRegistrationDate(individual.getRegistrationDate());
        individualContract.setDateOfBirth(individual.getDateOfBirth());
        if (individual.getGender() != null) {
            individualContract.setGender(constructGenderContract(individual.getGender()));
        }
        if (individual.getSubjectType() != null) {
            individualContract.setSubjectType(constructSubjectType(individual.getSubjectType()));
        }
        if (individual.getAddressLevel() != null) {
            individualContract.setLowestAddressLevel(constructAddressLevel(individual.getAddressLevel()));
        }
        if (individual.getObservations() != null) {
            List<ObservationModelContract> observationModelContracts = individual.getObservations()
                    .entrySet()
                    .stream()
                    .map(obs -> new ObservationRequestEntity(obs.getKey(), obs.getValue()))
                    .map(observationConstructionService::constructObservation)
                    .collect(Collectors.toList());
            individualContract.setObservations(observationModelContracts);
        }
        return individualContract;
    }
}

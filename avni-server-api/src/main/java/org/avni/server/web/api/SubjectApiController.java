package org.avni.server.web.api;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.geo.Point;
import org.avni.server.service.*;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.util.S;
import org.avni.server.web.request.api.ApiSubjectRequest;
import org.avni.server.web.request.api.RequestUtils;
import org.avni.server.web.response.ResponsePage;
import org.avni.server.web.response.SubjectResponse;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static org.avni.server.web.request.api.ApiSubjectRequest.*;

@RestController
public class SubjectApiController {
    private final ConceptService conceptService;
    private final IndividualRepository individualRepository;
    private final ConceptRepository conceptRepository;
    private final GroupSubjectRepository groupSubjectRepository;
    private final LocationService locationService;
    private final SubjectTypeRepository subjectTypeRepository;
    private final LocationRepository locationRepository;
    private final GenderRepository genderRepository;
    private final SubjectMigrationService subjectMigrationService;
    private final IndividualService individualService;
    private final S3Service s3Service;
    private final MediaObservationService mediaObservationService;
    private final AccessControlService accessControlService;

    @Autowired
    public SubjectApiController(ConceptService conceptService, IndividualRepository individualRepository,
                                ConceptRepository conceptRepository, GroupSubjectRepository groupSubjectRepository,
                                LocationService locationService, SubjectTypeRepository subjectTypeRepository,
                                LocationRepository locationRepository, GenderRepository genderRepository,
                                SubjectMigrationService subjectMigrationService, IndividualService individualService,
                                S3Service s3Service, MediaObservationService mediaObservationService, AccessControlService accessControlService) {
        this.conceptService = conceptService;
        this.individualRepository = individualRepository;
        this.conceptRepository = conceptRepository;
        this.groupSubjectRepository = groupSubjectRepository;
        this.locationService = locationService;
        this.subjectTypeRepository = subjectTypeRepository;
        this.locationRepository = locationRepository;
        this.genderRepository = genderRepository;
        this.subjectMigrationService = subjectMigrationService;
        this.individualService = individualService;
        this.s3Service = s3Service;
        this.mediaObservationService = mediaObservationService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/api/subjects", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getSubjects(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                    @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                    @RequestParam(value = "subjectType", required = false) String subjectType,
                                    @RequestParam(value = "concepts", required = false) String concepts,
                                    @RequestParam(value = "locationIds", required = false) List<String> locationUUIDs,
                                    Pageable pageable) {
        Page<Individual> subjects;
        boolean subjectTypeRequested = S.isEmpty(subjectType);
        List<Long> allLocationIds = locationService.getAllWithChildrenForUUIDs(locationUUIDs);
        Map<Concept, String> conceptsMap = conceptService.readConceptsFromJsonObject(concepts);
        subjects = subjectTypeRequested ?
                individualRepository.findByConcepts(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), conceptsMap, allLocationIds, pageable) :
                individualRepository.findByConceptsAndSubjectType(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), conceptsMap, subjectType, allLocationIds, pageable);
        List<GroupSubject> groupsOfAllMemberSubjects = groupSubjectRepository.findAllByMemberSubjectIn(subjects.getContent());
        ArrayList<SubjectResponse> subjectResponses = new ArrayList<>();
        subjects.forEach(subject -> {
            subjectResponses.add(SubjectResponse.fromSubject(subject, subjectTypeRequested, conceptRepository, conceptService, findGroupAffiliation(subject, groupsOfAllMemberSubjects), s3Service));
        });
        accessControlService.checkSubjectPrivileges(PrivilegeType.ViewSubject, subjects.getContent());
        return new ResponsePage(subjectResponses, subjects.getNumberOfElements(), subjects.getTotalPages(), subjects.getSize());
    }

    @GetMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<SubjectResponse> get(@PathVariable("id") String legacyIdOrUuid) {
        Individual subject = individualRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (subject == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, subject.getSubjectType());
        List<GroupSubject> groupsOfAllMemberSubjects = groupSubjectRepository.findAllByMemberSubjectIn(Collections.singletonList(subject));
        return new ResponseEntity<>(SubjectResponse.fromSubject(subject, true, conceptRepository, conceptService, groupsOfAllMemberSubjects, s3Service), HttpStatus.OK);
    }

    @PostMapping(value = "/api/subject")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity post(@RequestBody ApiSubjectRequest request) throws IOException {
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, request.getSubjectType());
        Individual subject = getOrCreateSubject(request.getExternalId());
        return updateSubjectAndSave(request, subject);
    }

    private ResponseEntity updateSubjectAndSave(@RequestBody ApiSubjectRequest request, Individual subject) throws IOException {
        try {
            updateSubjectDetails(subject, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        mediaObservationService.processMediaObservations(subject.getObservations());
        Individual savedIndividual = individualService.save(subject);
        return new ResponseEntity<>(SubjectResponse.fromSubject(savedIndividual, true, conceptRepository, conceptService, s3Service), HttpStatus.OK);
    }

    @PutMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity put(@PathVariable String id, @RequestBody ApiSubjectRequest request) throws IOException, ValidationException {
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, request.getSubjectType());
        Individual subject = loadSubject(id, request.getExternalId());
        return updateSubjectAndSave(request, subject);
    }

    private Individual loadSubject(String id, String externalId) {
        Individual subject = individualRepository.findByUuid(id);
        if (subject == null && StringUtils.hasLength(externalId)) {
            subject = individualRepository.findByLegacyId(externalId.trim());
        }
        if (subject == null) {
            throw new IllegalArgumentException(String.format("Subject not found with id '%s' or External ID '%s'", id, externalId));
        }
        return subject;
    }

    @PatchMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity patch(@PathVariable String id, @RequestBody Map<String, Object> request) throws IOException {
        accessControlService.checkSubjectPrivilege(PrivilegeType.EditSubject, (String) request.get(SUBJECT_TYPE));
        Individual subject = loadSubject(id, (String) request.get(CommonFieldNames.EXTERNAL_ID));
        try {
            patchSubject(subject, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        Set<String> observationKeys = request.containsKey(OBSERVATIONS) ? ((Map<String, Object>) request.get(OBSERVATIONS)).keySet() : new HashSet<>();
        mediaObservationService.patchMediaObservations(subject.getObservations(), observationKeys);
        Individual savedIndividual = individualService.save(subject);
        return new ResponseEntity<>(SubjectResponse.fromSubject(savedIndividual, true, conceptRepository, conceptService, s3Service), HttpStatus.OK);
    }

    @DeleteMapping(value = "/api/subject/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<SubjectResponse> delete(@PathVariable("id") String legacyIdOrUuid) {
        Individual subject = individualRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (subject == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        accessControlService.checkSubjectPrivilege(PrivilegeType.VoidSubject, subject);
        List<GroupSubject> groupsOfAllMemberSubjects = groupSubjectRepository.findAllByMemberSubjectIn(Collections.singletonList(subject));
        subject = individualService.voidSubject(subject);
        return new ResponseEntity<>(SubjectResponse.fromSubject(subject,
                true, conceptRepository, conceptService, groupsOfAllMemberSubjects, s3Service), HttpStatus.OK);
    }

    private void updateSubjectDetails(Individual subject, ApiSubjectRequest request) throws ValidationException {
        SubjectType subjectType = getSubjectType(request.getSubjectType());
        subject.setSubjectType(subjectType);
        Optional<AddressLevel> addressLevel = locationRepository.findByTitleLineageIgnoreCase(request.getAddress());
        if (!addressLevel.isPresent() && !subjectType.isAllowEmptyLocation()) {
            throw new IllegalArgumentException(String.format("Address '%s' not found", request.getAddress()));
        }
        setExternalId(subject, request.getExternalId());
        subject.setFirstName(request.getFirstName());
        setMiddleName(subject, subjectType, request.getMiddleName());

        subject.setLastName(request.getLastName());
        setProfilePicture(subject, subjectType, request.getProfilePicture());
        subject.setRegistrationDate(request.getRegistrationDate());
        ObservationCollection observations = RequestUtils.createObservations(request.getObservations(), conceptRepository);
        AddressLevel newAddressLevel = addressLevel.orElse(null);
        subjectMigrationService.markSubjectMigrationIfRequired(subject.getUuid(), newAddressLevel, observations);
        subject.setAddressLevel(newAddressLevel);
        if (subjectType.isPerson()) {
            subject.setDateOfBirth(request.getDateOfBirth());
            subject.setGender(genderRepository.findByName(request.getGender()));
        }
        subject.setObservations(observations);
        subject.setRegistrationLocation(request.getRegistrationLocation());
        subject.setVoided(request.isVoided());

        subject.validate();
    }

    private void setProfilePicture(Individual subject, SubjectType subjectType, String profilePicture) {
        if (subjectType.isAllowProfilePicture()) {
            subject.setProfilePicture(profilePicture);
        }
    }

    private void setMiddleName(Individual subject, SubjectType subjectType, String middleName) {
        if (subjectType.isAllowMiddleName())
            subject.setMiddleName(middleName);
    }

    private void setExternalId(Individual subject, String externalId) {
        if (StringUtils.hasLength(externalId)) {
            subject.setLegacyId(externalId.trim());
        }
    }

    private SubjectType getSubjectType(String subjectTypeName) {
        SubjectType subjectType = subjectTypeRepository.findByName(subjectTypeName);
        if (subjectType == null) {
            throw new IllegalArgumentException(String.format("Subject type not found with name '%s'", subjectTypeName));
        }
        return subjectType;
    }

    private void patchSubject(Individual subject, Map<String, Object> request) throws ValidationException {
        SubjectType subjectType;
        if (request.containsKey(SUBJECT_TYPE)) {
            subjectType = this.getSubjectType((String) request.get(SUBJECT_TYPE));
            subject.setSubjectType(subjectType);
        } else {
            subjectType = subject.getSubjectType();
        }

        if (request.containsKey(CommonFieldNames.EXTERNAL_ID)) {
            String externalId = (String) request.get(CommonFieldNames.EXTERNAL_ID);
            setExternalId(subject, externalId);
        }

        if (request.containsKey(FIRST_NAME))
            subject.setFirstName((String) request.get(FIRST_NAME));

        if (request.containsKey(MIDDLE_NAME))
            setMiddleName(subject, subjectType, (String) request.get(MIDDLE_NAME));

        if (request.containsKey(LAST_NAME))
            subject.setLastName((String) request.get(LAST_NAME));

        if (request.containsKey(PROFILE_PICTURE))
            setProfilePicture(subject, subjectType, (String) request.get(PROFILE_PICTURE));

        if (request.containsKey(REGISTRATION_DATE))
            subject.setRegistrationDate(DateTimeUtil.parseNullableDate(request.get(REGISTRATION_DATE)));

        if (request.containsKey(OBSERVATIONS))
            RequestUtils.patchObservations((Map<String, Object>) request.get(OBSERVATIONS), conceptRepository, subject.getObservations());

        if (request.containsKey(ADDRESS)) {
            String locationTitleLineage = (String) request.get(ADDRESS);
            Optional<AddressLevel> addressLevel = locationRepository.findByTitleLineageIgnoreCase(locationTitleLineage);
            AddressLevel newAddressLevel = addressLevel.orElseThrow(() -> new IllegalArgumentException(String.format("Address '%s' not found", locationTitleLineage)));
            subject.setAddressLevel(newAddressLevel);
            subjectMigrationService.markSubjectMigrationIfRequired(subject.getUuid(), newAddressLevel, subject.getObservations());
        }

        if (subject.getSubjectType().isPerson()) {
            if (request.containsKey(DATE_OF_BIRTH))
                subject.setDateOfBirth(LocalDate.parse((String) request.get(DATE_OF_BIRTH)));
            if (request.containsKey(GENDER))
                subject.setGender(genderRepository.findByName((String) request.get(GENDER)));
        }

        if (request.containsKey(REGISTRATION_LOCATION)) {
            Point registrationPoint = Point.fromMap((Map<String, Double>) request.get(REGISTRATION_LOCATION));
            subject.setRegistrationLocation(registrationPoint);
        }

        if (request.containsKey(CommonFieldNames.VOIDED))
            subject.setVoided((Boolean) request.get(CommonFieldNames.VOIDED));

        subject.validate();
    }

    private List<GroupSubject> findGroupAffiliation(Individual subject, List<GroupSubject> groupSubjects) {
        return groupSubjects.stream().filter(groupSubject -> groupSubject.getMemberSubject().equals(subject)).collect(Collectors.toList());
    }

    private Individual getOrCreateSubject(String externalId) {
        if (StringUtils.hasLength(externalId)) {
            Individual individual = individualRepository.findByLegacyId(externalId.trim());
            if (individual != null) {
                return individual;
            }
        }
        Individual subject = new Individual();
        subject.assignUUID();
        if (StringUtils.hasLength(externalId)) {
            subject.setLegacyId(externalId.trim());
        }
        return subject;
    }
}

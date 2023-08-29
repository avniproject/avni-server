package org.avni.server.web.api;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.ConceptService;
import org.avni.server.service.EncounterService;
import org.avni.server.service.MediaObservationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.api.ApiEncounterRequest;
import org.avni.server.web.request.api.RequestUtils;
import org.avni.server.web.response.EncounterResponse;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import static org.avni.server.web.request.api.ApiBaseEncounterRequest.*;

@RestController
public class GeneralEncounterApiController {
    private final ConceptService conceptService;
    private final EncounterRepository encounterRepository;
    private final ConceptRepository conceptRepository;
    private final IndividualRepository individualRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final EncounterService encounterService;
    private final MediaObservationService mediaObservationService;
    private final AccessControlService accessControlService;

    @Autowired
    public GeneralEncounterApiController(ConceptService conceptService, EncounterRepository encounterRepository, ConceptRepository conceptRepository, IndividualRepository individualRepository, EncounterTypeRepository encounterTypeRepository, EncounterService encounterService, MediaObservationService mediaObservationService, AccessControlService accessControlService) {
        this.conceptService = conceptService;
        this.encounterRepository = encounterRepository;
        this.conceptRepository = conceptRepository;
        this.individualRepository = individualRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.encounterService = encounterService;
        this.mediaObservationService = mediaObservationService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/api/encounters", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getEncounters(@RequestParam(value = "lastModifiedDateTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                      @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                      @RequestParam(value = "encounterType", required = false) String encounterType,
                                      @RequestParam(value = "subjectId", required = false) String subjectUUID,
                                      @RequestParam(value = "concepts", required = false) String concepts,
                                      Pageable pageable) {
        Page<Encounter> encounters;
        Map<Concept, String> conceptsMap = conceptService.readConceptsFromJsonObject(concepts);

        EncounterSearchRequest encounterSearchRequest = new EncounterSearchRequest(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), encounterType, subjectUUID, conceptsMap, pageable);

        encounters = encounterService.search(encounterSearchRequest);

        ArrayList<EncounterResponse> encounterResponses = new ArrayList<>();
        encounters.forEach(encounter -> {
            encounterResponses.add(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService));
        });
        accessControlService.checkEncounterPrivileges(PrivilegeType.ViewVisit, encounters.getContent());
        return new ResponsePage(encounterResponses, encounters.getNumberOfElements(), encounters.getTotalPages(), encounters.getSize());
    }

    @GetMapping(value = "/api/encounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EncounterResponse> get(@PathVariable("id") String legacyIdOrUuid) {
        Encounter encounter = encounterRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (encounter == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        accessControlService.checkEncounterPrivilege(PrivilegeType.ViewVisit, encounter);
        return new ResponseEntity<>(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    @PostMapping(value = "/api/encounter")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity post(@RequestBody ApiEncounterRequest request) throws IOException {
        accessControlService.checkEncounterPrivilege(PrivilegeType.EditVisit, request.getEncounterType());
        Encounter encounter = createEncounter(request.getExternalId());
        try {
            initializeIndividual(request, encounter);
            updateEncounter(encounter, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    private void initializeIndividual(ApiEncounterRequest request, Encounter encounter) {
        Individual individual = null;
        if (individual == null && StringUtils.hasLength(request.getSubjectId())) {
            individual = individualRepository.findByLegacyIdOrUuid(request.getSubjectId());
        }
        if (individual == null && StringUtils.hasLength(request.getSubjectExternalId())) {
            individual = individualRepository.findByLegacyId(request.getSubjectExternalId().trim());
        }
        if (individual == null) {
            throw new IllegalArgumentException(String.format("Individual not found with UUID '%s' or External ID '%s'", request.getSubjectId(), request.getSubjectExternalId()));
        }
        encounter.setIndividual(individual);
    }

    @PutMapping(value = "/api/encounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity put(@PathVariable String id, @RequestBody ApiEncounterRequest request) throws IOException {
        accessControlService.checkEncounterPrivilege(PrivilegeType.EditVisit, request.getEncounterType());
        Encounter encounter = encounterRepository.findByLegacyIdOrUuid(id);
        if (encounter == null && StringUtils.hasLength(request.getExternalId())) {
            encounter = encounterRepository.findByLegacyId(request.getExternalId().trim());
        }
        if (encounter == null) {
            throw new IllegalArgumentException(String.format("Encounter not found with id '%s' or External ID '%s'", id, request.getExternalId()));
        }
        try {
            encounter = updateEncounter(encounter, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    @PatchMapping(value = "/api/encounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity patch(@PathVariable String id, @RequestBody Map<String, Object> request) throws IOException {
        accessControlService.checkEncounterPrivilege(PrivilegeType.EditVisit, (String) request.get(ENCOUNTER_TYPE));
        Encounter encounter = encounterRepository.findByLegacyIdOrUuid(id);
        String externalId = (String) request.get(EXTERNAL_ID);
        if (encounter == null && StringUtils.hasLength(externalId)) {
            encounter = encounterRepository.findByLegacyId(externalId.trim());
        }
        if (encounter == null) {
            throw new IllegalArgumentException(String.format("Encounter not found with id '%s' or External ID '%s'", id, externalId));
        }
        try {
            encounter = encounterService.patchEncounter(encounter, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    @DeleteMapping(value = "/api/encounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EncounterResponse> delete(@PathVariable("id") String legacyIdOrUuid) {
        Encounter encounter = encounterRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (encounter == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        accessControlService.checkEncounterPrivilege(PrivilegeType.VoidVisit, encounter);
        encounter.setVoided(true);
        encounter = encounterService.save(encounter);
        return new ResponseEntity<>(EncounterResponse.fromEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    private Encounter updateEncounter(Encounter encounter, ApiEncounterRequest request) throws ValidationException, IOException {
        EncounterType encounterType = encounterTypeRepository.findByName(request.getEncounterType());
        if (encounterType == null) {
            throw new IllegalArgumentException(String.format("Encounter type not found with name '%s'", request.getEncounterType()));
        }
        if(StringUtils.hasLength(request.getExternalId())) {
            encounter.setLegacyId(request.getExternalId().trim());
        }
        encounter.setEncounterType(encounterType);
        encounter.setEncounterLocation(request.getEncounterLocation());
        encounter.setCancelLocation(request.getCancelLocation());
        encounter.setEncounterDateTime(request.getEncounterDateTime());
        encounter.setEarliestVisitDateTime(request.getEarliestScheduledDate());
        encounter.setMaxVisitDateTime(request.getMaxScheduledDate());
        encounter.setCancelDateTime(request.getCancelDateTime());
        encounter.setObservations(RequestUtils.createObservations(request.getObservations(), conceptRepository));
        encounter.setCancelObservations(RequestUtils.createObservations(request.getCancelObservations(), conceptRepository));
        encounter.setVoided(request.isVoided());

        encounter.validate();
        mediaObservationService.processMediaObservations(encounter.getObservations());
        mediaObservationService.processMediaObservations(encounter.getCancelObservations());
        return encounterService.save(encounter);
    }

    private Encounter createEncounter(String externalId) {
        if (StringUtils.hasLength(externalId)) {
            Encounter encounter = encounterRepository.findByLegacyId(externalId.trim());
            if(encounter != null) {
                return encounter;
            }
        }
        Encounter encounter = new Encounter();
        encounter.assignUUID();
        if (StringUtils.hasLength(externalId)) {
            encounter.setLegacyId(externalId.trim());
        }
        return encounter;
    }
}

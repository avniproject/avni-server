package org.avni.server.web.api;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.ConceptService;
import org.avni.server.service.MediaObservationService;
import org.avni.server.service.ProgramEncounterService;
import org.avni.server.service.UserService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.S;
import org.avni.server.util.TimeTakenLogger;
import org.avni.server.web.request.api.ApiProgramEncounterRequest;
import org.avni.server.web.request.api.RequestUtils;
import org.avni.server.web.response.EncounterResponse;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@RestController
public class ProgramEncounterApiController {
    private final ProgramEncounterRepository programEncounterRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptService conceptService;
    private final ProgramEnrolmentRepository programEnrolmentRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ProgramEncounterService programEncounterService;
    private final MediaObservationService mediaObservationService;
    private final AccessControlService accessControlService;
    private final UserService userService;
    private static final Logger logger = LoggerFactory.getLogger(ProgramEncounterApiController.class);

    @Autowired
    public ProgramEncounterApiController(ProgramEncounterRepository programEncounterRepository, ConceptRepository conceptRepository, ConceptService conceptService, ProgramEnrolmentRepository programEnrolmentRepository, EncounterTypeRepository encounterTypeRepository, ProgramEncounterService programEncounterService, MediaObservationService mediaObservationService, AccessControlService accessControlService, UserService userService) {
        this.programEncounterRepository = programEncounterRepository;
        this.conceptRepository = conceptRepository;
        this.conceptService = conceptService;
        this.programEnrolmentRepository = programEnrolmentRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.programEncounterService = programEncounterService;
        this.mediaObservationService = mediaObservationService;
        this.accessControlService = accessControlService;
        this.userService = userService;
    }

    @RequestMapping(value = "/api/programEncounters", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getEncounters(@RequestParam(name = "lastModifiedDateTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                      @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                      @RequestParam(value = "encounterType", required = false) String encounterType,
                                      @RequestParam(value = "concepts", required = false) String concepts,
                                      @RequestParam(value = "programEnrolmentId", required = false) String programEnrolmentUuid,
                                      Pageable pageable) {
        TimeTakenLogger timeTakenLogger = new TimeTakenLogger("Search program encounters", logger);
        Page<ProgramEncounter> programEncounters = programEncounterRepository.search(createSearchParams(lastModifiedDateTime, now, encounterType, concepts, programEnrolmentUuid), pageable);
        timeTakenLogger.logInfo();

        ArrayList<EncounterResponse> programEncounterResponses = new ArrayList<>();
        programEncounters.forEach(programEncounter -> {
            programEncounterResponses.add(EncounterResponse.fromProgramEncounter(programEncounter, conceptRepository, conceptService));
        });
        accessControlService.checkProgramEncounterPrivileges(PrivilegeType.ViewVisit, programEncounters.getContent());
        return new ResponsePage(programEncounterResponses, programEncounters.getNumberOfElements(), programEncounters.getTotalPages(), programEncounters.getSize());
    }

    public ProgramEncounterRepository.SearchParams createSearchParams(DateTime lastModifiedDateTime,
                                                                      DateTime now,
                                                                      String encounterTypeStr,
                                                                      String concepts, String programEnrolmentUuid) {
        EncounterType encounterType = null;
        if (!S.isEmpty(encounterTypeStr)) {
            encounterType = encounterTypeRepository.findByName(encounterTypeStr);
        }
        Map<Concept, String> conceptsMap = conceptService.readConceptsFromJsonObject(concepts);
        ProgramEnrolment programEnrolment = null;
        if (!S.isEmpty(programEnrolmentUuid)) {
            programEnrolment = programEnrolmentRepository.findByUuid(programEnrolmentUuid);
        }

        return new ProgramEncounterRepository.SearchParams(CHSEntity.toDate(lastModifiedDateTime), CHSEntity.toDate(now), conceptsMap, encounterType, programEnrolment);
    }

    @GetMapping(value = "/api/programEncounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EncounterResponse> get(@PathVariable("id") String legacyIdOrUuid) {
        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (programEncounter == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);

        accessControlService.checkProgramEncounterPrivilege(PrivilegeType.ViewVisit, programEncounter);
        return new ResponseEntity<>(EncounterResponse.fromProgramEncounter(programEncounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    @PostMapping(value = "/api/programEncounter")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity post(@RequestBody ApiProgramEncounterRequest request) throws IOException {
        accessControlService.checkProgramEncounterPrivilege(PrivilegeType.EditVisit, request.getEncounterType());
        ProgramEncounter encounter = createEncounter(request.getExternalId());
        try {
            ProgramEnrolment programEnrolment = getProgramEnrolment(request);
            encounter.setProgramEnrolment(programEnrolment);
            encounter = updateEncounter(encounter, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(EncounterResponse.fromProgramEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    private ProgramEnrolment getProgramEnrolment(ApiProgramEncounterRequest request) {
        ProgramEnrolment programEnrolment = null;
        if (programEnrolment == null && StringUtils.hasLength(request.getEnrolmentId())) {
            programEnrolment = programEnrolmentRepository.findByLegacyIdOrUuid(request.getEnrolmentId());
        }
        if (programEnrolment == null && StringUtils.hasLength(request.getProgramEnrolmentExternalId())) {
            programEnrolment = programEnrolmentRepository.findByLegacyId(request.getProgramEnrolmentExternalId().trim());
        }
        if (programEnrolment == null) {
            throw new IllegalArgumentException(String.format("ProgramEnrolment not found with UUID '%s' or External ID '%s'", request.getEnrolmentId(), request.getProgramEnrolmentExternalId()));
        }
        return programEnrolment;
    }

    @PutMapping(value = "/api/programEncounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional
    @ResponseBody
    public ResponseEntity put(@PathVariable String id, @RequestBody ApiProgramEncounterRequest request) throws IOException {
        accessControlService.checkProgramEncounterPrivilege(PrivilegeType.EditVisit, request.getEncounterType());
        ProgramEncounter encounter = programEncounterRepository.findByLegacyIdOrUuid(id);
        if (encounter == null && StringUtils.hasLength(request.getExternalId())) {
            encounter = programEncounterRepository.findByLegacyId(request.getExternalId().trim());
        }
        if (encounter == null) {
            throw new IllegalArgumentException(String.format("Encounter not found with id '%s' or External ID '%s'", id, request.getExternalId()));
        }
        try {
            encounter = updateEncounter(encounter, request);
        } catch (ValidationException ve) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ve.getMessage());
        }
        return new ResponseEntity<>(EncounterResponse.fromProgramEncounter(encounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    @DeleteMapping(value = "/api/programEncounter/{id}")
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<EncounterResponse> delete(@PathVariable("id") String legacyIdOrUuid) {
        ProgramEncounter programEncounter = programEncounterRepository.findByLegacyIdOrUuid(legacyIdOrUuid);
        if (programEncounter == null)
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        accessControlService.checkProgramEncounterPrivilege(PrivilegeType.VoidVisit, programEncounter);
        programEncounter.setVoided(true);
        programEncounter = programEncounterService.save(programEncounter);
        return new ResponseEntity<>(EncounterResponse.fromProgramEncounter(programEncounter, conceptRepository, conceptService), HttpStatus.OK);
    }

    private ProgramEncounter updateEncounter(ProgramEncounter encounter, ApiProgramEncounterRequest request) throws ValidationException, IOException {
        EncounterType encounterType = encounterTypeRepository.findByName(request.getEncounterType());
        if (encounterType == null) {
            throw new IllegalArgumentException(String.format("Encounter type not found with name '%s'", request.getEncounterType()));
        }
        if (StringUtils.hasLength(request.getExternalId())) {
            encounter.setLegacyId(request.getExternalId().trim());
        }
        encounter.setEncounterType(encounterType);
        encounter.setEncounterLocation(request.getEncounterLocation());
        encounter.setCancelLocation(request.getCancelLocation());
        encounter.setEncounterDateTime(request.getEncounterDateTime(), userService.getCurrentUser());
        encounter.setEarliestVisitDateTime(request.getEarliestScheduledDate());
        encounter.setMaxVisitDateTime(request.getMaxScheduledDate());
        encounter.setCancelDateTime(request.getCancelDateTime());
        encounter.setObservations(RequestUtils.createObservations(request.getObservations(), conceptRepository));
        encounter.setCancelObservations(RequestUtils.createObservations(request.getCancelObservations(), conceptRepository));
        encounter.setVoided(request.isVoided());

        encounter.validate();
        mediaObservationService.processMediaObservations(encounter.getObservations());
        mediaObservationService.processMediaObservations(encounter.getCancelObservations());
        return programEncounterService.save(encounter);
    }

    private ProgramEncounter createEncounter(String externalId) {
        if (StringUtils.hasLength(externalId)) {
            ProgramEncounter encounter = programEncounterRepository.findByLegacyId(externalId.trim());
            if (encounter != null) {
                return encounter;
            }
        }
        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.assignUUID();
        if (StringUtils.hasLength(externalId)) {
            programEncounter.setLegacyId(externalId.trim());
        }
        return programEncounter;
    }
}

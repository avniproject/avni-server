package org.openchs.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openchs.dao.*;
import org.openchs.dao.application.FormElementGroupRepository;
import org.openchs.dao.application.FormElementRepository;
import org.openchs.domain.JsonObject;
import org.openchs.domain.Organisation;
import org.openchs.domain.Translation;
import org.openchs.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
public class TranslationController implements RestControllerResourceProcessor<Translation> {

    private final TranslationRepository translationRepository;
    private final ObjectMapper mapper;
    private final Logger logger;
    private final FormElementGroupRepository formElementGroupRepository;
    private final FormElementRepository formElementRepository;
    private final ConceptRepository conceptRepository;
    private final ConceptAnswerRepository conceptAnswerRepository;
    private final OperationalEncounterTypeRepository operationalEncounterTypeRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final OperationalProgramRepository operationalProgramRepository;
    private final ProgramRepository programRepository;
    private final ChecklistDetailRepository checklistDetailRepository;
    private final CatchmentRepository catchmentRepository;
    private final LocationRepository locationRepository;

    @Autowired
    TranslationController(TranslationRepository translationRepository,
                          ObjectMapper mapper,
                          FormElementGroupRepository formElementGroupRepository,
                          FormElementRepository formElementRepository,
                          ConceptRepository conceptRepository,
                          ConceptAnswerRepository conceptAnswerRepository,
                          OperationalEncounterTypeRepository operationalEncounterTypeRepository,
                          EncounterTypeRepository encounterTypeRepository,
                          OperationalProgramRepository operationalProgramRepository,
                          ProgramRepository programRepository,
                          ChecklistDetailRepository checklistDetailRepository,
                          CatchmentRepository catchmentRepository,
                          LocationRepository locationRepository) {
        this.translationRepository = translationRepository;
        this.formElementGroupRepository = formElementGroupRepository;
        this.formElementRepository = formElementRepository;
        this.conceptRepository = conceptRepository;
        this.conceptAnswerRepository = conceptAnswerRepository;
        this.operationalEncounterTypeRepository = operationalEncounterTypeRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.operationalProgramRepository = operationalProgramRepository;
        this.programRepository = programRepository;
        this.checklistDetailRepository = checklistDetailRepository;
        this.catchmentRepository = catchmentRepository;
        this.locationRepository = locationRepository;
        this.mapper = mapper;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/translation", method = RequestMethod.POST)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    public ResponseEntity<?> uploadTranslations(@RequestParam("translationFile") MultipartFile translationFile) throws Exception {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        Translation translation = translationRepository.findByOrganisationId(organisation.getId());
        if (translation == null) {
            translation = new Translation();
        }
        String jsonContent = new BufferedReader(new InputStreamReader(translationFile.getInputStream()))
                .lines()
                .parallel()
                .collect(Collectors.joining("\n"));
        try {
            JsonObject json = mapper.readValue(jsonContent, JsonObject.class);
            translation.setTranslationJson(json);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
        translation.assignUUIDIfRequired();
        translationRepository.save(translation);
        logger.info(String.format("Saved Translation with UUID: %s", translation.getUuid()));
        return new ResponseEntity<>(true, HttpStatus.OK);
    }
    //TODO: what all keys to pass? (core and impl)
    //TODO: How to get core keys?
    //TODO: include product keys to same JSON?
    //TODO: which all language to pass? (check it from organisation config)
    //TODO: default values for rest of the keys?

    @RequestMapping(value = "/translation", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    public ResponseEntity<?> downloadTranslations() {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        Map<String, String> result = new HashMap<>();
        Translation translation = translationRepository.findByOrganisationId(organisation.getId());
        if (translation == null) {
            logger.info(String.format("Translation for organisation '%s' not found", organisation.getName()));
            formElementGroupRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            formElementRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            conceptRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            operationalEncounterTypeRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            encounterTypeRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            operationalProgramRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            programRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            checklistDetailRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            catchmentRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getName(), ""));
            locationRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getTitle(), ""));
            conceptAnswerRepository.findByOrganisationId(organisation.getId())
                    .forEach(e -> result.put(e.getConcept().getName(), ""));
            return ResponseEntity.ok().body(new JsonObject()
                    .with("mr_IN", result)
                    .with("en", result)
                    .with("gu_IN", result)
                    .with("hi_IN", result)
            );
        }
        return ResponseEntity.ok()
                //.header("Content-disposition", "attachment; filename=" + organisation.getName().concat("_translations.json"))
                //.contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(translation.getTranslationJson());
    }
}

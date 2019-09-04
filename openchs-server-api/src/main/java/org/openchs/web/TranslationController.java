package org.openchs.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openchs.dao.TranslationRepository;
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
import java.util.stream.Collectors;

@RestController
public class TranslationController implements RestControllerResourceProcessor<Translation> {

    private final TranslationRepository translationRepository;
    private final ObjectMapper mapper;
    private final Logger logger;

    @Autowired
    TranslationController(TranslationRepository translationRepository, ObjectMapper mapper) {
        this.translationRepository = translationRepository;
        this.mapper = mapper;
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @RequestMapping(value = "/translationImport", method = RequestMethod.POST)
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

    @RequestMapping(value = "/translationExport", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    public ResponseEntity<?> downloadTranslations(@RequestParam(value = "fileName") String fileName) {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        Translation translation = translationRepository.findByOrganisationId(organisation.getId());
        if (translation == null) {
            return ResponseEntity.badRequest()
                    .body(String.format("Translation for organisation '%s' not found", organisation.getName()));
        }
        return ResponseEntity.ok()
                .header("Content-disposition", "attachment; filename=" + fileName)
                .contentType(MediaType.parseMediaType("application/octet-stream"))
                .body(translation.getTranslationJson());
    }
}

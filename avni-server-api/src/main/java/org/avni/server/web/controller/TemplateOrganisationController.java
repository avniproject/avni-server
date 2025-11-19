package org.avni.server.web.controller;

import jakarta.transaction.Transactional;
import org.avni.server.dao.TemplateOrganisationRepository;
import org.avni.server.domain.TemplateOrganisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.batch.BatchJobStatus;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.TemplateOrganisationService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.TemplateOrganisationContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class TemplateOrganisationController {
    private final TemplateOrganisationRepository templateOrganisationRepository;
    private final TemplateOrganisationService templateOrganisationService;
    private final AccessControlService accessControlService;

    @Autowired
    public TemplateOrganisationController(TemplateOrganisationRepository templateOrganisationRepository,
                                          TemplateOrganisationService templateOrganisationService, AccessControlService accessControlService) {
        this.templateOrganisationRepository = templateOrganisationRepository;
        this.templateOrganisationService = templateOrganisationService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/web/templateOrganisations", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> save(@RequestBody TemplateOrganisationContract request) {
        accessControlService.assertIsSuperAdmin();
        TemplateOrganisation templateOrganisation = templateOrganisationService.save(request);
        return new ResponseEntity<>(templateOrganisation, HttpStatus.CREATED);
    }

    @RequestMapping(value = "/web/templateOrganisations/{id}", method = RequestMethod.PUT)
    @Transactional
    public ResponseEntity<?> update(@PathVariable("id") Long id, @RequestBody TemplateOrganisationContract request) {
        accessControlService.assertIsSuperAdmin();
        TemplateOrganisation templateOrganisation = templateOrganisationService.update(id, request);
        return new ResponseEntity<>(templateOrganisation, HttpStatus.OK);
    }

    @RequestMapping(value = "/web/templateOrganisations", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<?> getAll() {
        List<TemplateOrganisation> templateOrganisations = templateOrganisationRepository.findByActiveTrue();
        List<TemplateOrganisationContract> response = templateOrganisations.stream()
                .map(TemplateOrganisationContract::fromEntity)
                .collect(Collectors.toList());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/web/templateOrganisations/all", method = RequestMethod.GET)
    @ResponseBody
    public ResponseEntity<?> getAllIncludingInactive() {
        accessControlService.assertIsSuperAdmin();
        List<TemplateOrganisation> templateOrganisations = templateOrganisationRepository.findAll();
        List<TemplateOrganisationContract> response = templateOrganisations.stream()
                .map(TemplateOrganisationContract::fromEntity)
                .collect(Collectors.toList());
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @RequestMapping(value = "/web/templateOrganisations/{id}", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    public ResponseEntity<?> getOne(@PathVariable("id") Long id) {
        Optional<TemplateOrganisation> templateOrganisation = templateOrganisationRepository.findById(id);
        if (templateOrganisation.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        return new ResponseEntity<>(TemplateOrganisationContract.fromEntity(templateOrganisation.orElse(null)), HttpStatus.OK);
    }

//    @RequestMapping(value = "/templateOrganisations/{id}", method = RequestMethod.DELETE)
//    @PreAuthorize(value = "hasAnyAuthority('organisation_admin')")
//    @Transactional
//    public ResponseEntity<?> delete(@PathVariable("id") Long id) {
//        TemplateOrganisation templateOrganisation = templateOrganisationRepository.findById(id);
//        if (templateOrganisation == null) {
//            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
//        }
//        templateOrganisation.setVoided(true);
//        templateOrganisationRepository.save(templateOrganisation);
//        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
//    }

    @RequestMapping(value = "/web/templateOrganisations/{id}/toggleActive", method = RequestMethod.PUT)
    public ResponseEntity<?> toggleActive(@PathVariable("id") Long id) {
        accessControlService.assertIsSuperAdmin();
        Optional<TemplateOrganisation> templateOrganisation = templateOrganisationRepository.findById(id);
        if (templateOrganisation.isEmpty()) {
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        TemplateOrganisation template = templateOrganisation.get();
        template.setActive(!template.isActive());
        templateOrganisationRepository.save(template);
        return new ResponseEntity<>(TemplateOrganisationContract.fromEntity(template), HttpStatus.OK);
    }

    @RequestMapping(value = "/web/templateOrganisations/{id}/apply", method = RequestMethod.POST)
    public ResponseEntity<?> applyTemplate(@PathVariable("id") Long id) {
        accessControlService.assertIsNotSuperAdmin();
        accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        accessControlService.checkPrivilege(PrivilegeType.DeleteOrganisationConfiguration);
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        Optional<TemplateOrganisation> templateOrganisation = templateOrganisationRepository.findById(id);
        if (templateOrganisation.isEmpty()) {
            return new ResponseEntity<>("Template not found.", HttpStatus.NOT_FOUND);
        }
        TemplateOrganisation template = templateOrganisation.get();
        if (!template.isActive()) {
            return new ResponseEntity<>("Template not active.", HttpStatus.BAD_REQUEST);
        }
        String jobUuid;
        try {
            jobUuid = templateOrganisationService.applyTemplate(template);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return new ResponseEntity<>(jobUuid, HttpStatus.ACCEPTED);
    }

    @RequestMapping(value = "/web/templateOrganisations/apply/status", method = RequestMethod.GET)
    public Map<String, BatchJobStatus> applyTemplateJobStatus() {
        accessControlService.checkPrivilege(PrivilegeType.UploadMetadataAndData);
        return templateOrganisationService.getApplyTemplateJobStatus(UserContextHolder.getOrganisation());
    }
}

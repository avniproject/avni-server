package org.avni.server.web;

import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.IdentifierSourceService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.webapp.IdentifierSourceContractWeb;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.transaction.Transactional;

@RestController
public class IdentifierSourceWebController extends AbstractController<IdentifierSource> implements RestControllerResourceProcessor<IdentifierSourceContractWeb> {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(IndividualController.class);
    private final IdentifierSourceRepository identifierSourceRepository;
    private final IdentifierSourceService identifierSourceService;
    private final AccessControlService accessControlService;

    @Autowired
    public IdentifierSourceWebController(IdentifierSourceRepository identifierSourceRepository, IdentifierSourceService identifierSourceService, AccessControlService accessControlService) {
        this.identifierSourceRepository = identifierSourceRepository;
        this.identifierSourceService = identifierSourceService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/identifierSource/search/findAllById")
    public CollectionModel<EntityModel<IdentifierSourceContractWeb>> findAllById(Long ids, Pageable pageable) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierSource);
        Long[] id = {ids};
        return wrap(identifierSourceRepository.findByIdIn(id, pageable).map(IdentifierSourceContractWeb::fromIdentifierSource));
    }

    @GetMapping(value = "/web/identifierSource")
    @ResponseBody
    public CollectionModel<EntityModel<IdentifierSourceContractWeb>> getAll(Pageable pageable) {
        return wrap(identifierSourceRepository.findPageByIsVoidedFalse(pageable).map(IdentifierSourceContractWeb::fromIdentifierSource));
    }

    @GetMapping(value = "/web/identifierSource/{id}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("id") Long id) {
        IdentifierSource identifierSource = identifierSourceRepository.findOne(id);
        if (identifierSource.isVoided())
            return ResponseEntity.notFound().build();
        return new ResponseEntity<>(IdentifierSourceContractWeb.fromIdentifierSource(identifierSource), HttpStatus.OK);
    }


    @PostMapping(value = "/web/identifierSource")
    @Transactional
    ResponseEntity saveProgramForWeb(@RequestBody IdentifierSourceContractWeb request) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierSource);
        IdentifierSource identifierSource = identifierSourceService.saveIdSource(request);
        return ResponseEntity.ok(IdentifierSourceContractWeb.fromIdentifierSource(identifierSource));
    }

    @PutMapping(value = "/web/identifierSource/{id}")
    @Transactional
    public ResponseEntity updateProgramForWeb(@RequestBody IdentifierSourceContractWeb request,
                                              @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierSource);
        IdentifierSource identifierSource = identifierSourceRepository.findOne(id);
        if (identifierSource == null)
            return ResponseEntity.badRequest()
                    .body(ReactAdminUtil.generateJsonError(String.format("Identifier source with id '%d' not found", id)));

        IdentifierSource savedEntity = identifierSourceService.updateIdSource(identifierSource, request);
        return ResponseEntity.ok(IdentifierSourceContractWeb.fromIdentifierSource(savedEntity));
    }

    @DeleteMapping(value = "/web/identifierSource/{id}")
    @Transactional
    public ResponseEntity voidProgram(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierSource);
        IdentifierSource identifierSource = identifierSourceRepository.findOne(id);
        if (identifierSource == null)
            return ResponseEntity.notFound().build();

        identifierSource.setVoided(true);
        identifierSourceRepository.save(identifierSource);
        return ResponseEntity.ok(null);
    }
}

package org.avni.server.web;

import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.identifier.IdentifierOverlappingException;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.identifier.IdentifierUserAssignmentService;
import org.avni.server.util.WebResponseUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.RestController;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.webapp.IdentifierUserAssignmentContractWeb;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.data.domain.Pageable;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import jakarta.transaction.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
public class IdentifierUserAssignmentWebController extends AbstractController<IdentifierUserAssignment> implements RestControllerResourceProcessor<IdentifierUserAssignmentContractWeb> {
    private final IdentifierUserAssignmentRepository identifierUserAssignmentRepository;
    private final UserRepository userRepository;
    private final IdentifierSourceRepository identifierSourceRepository;
    private final AccessControlService accessControlService;
    private final IdentifierUserAssignmentService identifierUserAssignmentService;
    private final static Logger logger = LoggerFactory.getLogger(IdentifierUserAssignmentWebController.class);;

    @Autowired
    public IdentifierUserAssignmentWebController(IdentifierUserAssignmentRepository identifierUserAssignmentRepository, UserRepository userRepository, IdentifierSourceRepository identifierSourceRepository, AccessControlService accessControlService, IdentifierUserAssignmentService identifierUserAssignmentService) {
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
        this.userRepository = userRepository;
        this.identifierSourceRepository = identifierSourceRepository;
        this.accessControlService = accessControlService;
        this.identifierUserAssignmentService = identifierUserAssignmentService;
    }

    @GetMapping(value = "/web/identifierUserAssignment")
    @ResponseBody
    public CollectionModel<EntityModel<IdentifierUserAssignmentContractWeb>> getAll(Pageable pageable) {
        Page<IdentifierUserAssignment> nonVoided = identifierUserAssignmentRepository.findPageByIsVoidedFalse(pageable);
        Page<IdentifierUserAssignmentContractWeb> response = nonVoided.map(IdentifierUserAssignmentContractWeb::fromIdentifierUserAssignment);
        return wrap(response);
    }

    @GetMapping(value = "/web/identifierUserAssignment/{id}")
    @ResponseBody
    public ResponseEntity getOne(@PathVariable("id") Long id) {
        IdentifierUserAssignment identifierUserAssignment = identifierUserAssignmentRepository.findOne(id);
        if (identifierUserAssignment == null || identifierUserAssignment.isVoided())
            return ResponseEntity.notFound().build();
        return new ResponseEntity<>(IdentifierUserAssignmentContractWeb.fromIdentifierUserAssignment(identifierUserAssignment), HttpStatus.OK);
    }

    @PostMapping(value = "/web/identifierUserAssignment")
    @Transactional
    public ResponseEntity saveIdentifierAssignment(@RequestBody IdentifierUserAssignmentContractWeb request) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierUserAssignment);
        IdentifierUserAssignment identifierUserAssignment = getIdentifierUserAssignment(request);

        identifierUserAssignment.assignUUID();
        identifierUserAssignment.setVoided(false);
        try {
            identifierUserAssignmentService.save(identifierUserAssignment);
        } catch (IdentifierOverlappingException | ValidationException e) {
            return WebResponseUtil.createBadRequestResponse(e, logger);
        }
        return ResponseEntity.ok(IdentifierUserAssignmentContractWeb.fromIdentifierUserAssignment(identifierUserAssignment));
    }

    @PutMapping(value = "/web/identifierUserAssignment/{id}")
    @Transactional
    public ResponseEntity updateIdAssignment(@RequestBody IdentifierUserAssignmentContractWeb request,
                                             @PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierUserAssignment);
        IdentifierUserAssignment existingIdentifierUserAssignment = identifierUserAssignmentRepository.findOne(id);
        if (existingIdentifierUserAssignment == null)
            return ResponseEntity.badRequest()
                    .body(ReactAdminUtil.generateJsonError(String.format("Identifier source with id '%d' not found", id)));

        IdentifierUserAssignment identifierUserAssignment = getIdentifierUserAssignment(request);
        identifierUserAssignment.setVoided(request.isVoided());
        try {
            IdentifierUserAssignment saved = identifierUserAssignmentService.update(existingIdentifierUserAssignment, identifierUserAssignment);
            return ResponseEntity.ok(IdentifierUserAssignmentContractWeb.fromIdentifierUserAssignment(saved));
        } catch (IdentifierOverlappingException | ValidationException e) {
            return WebResponseUtil.createBadRequestResponse(e, logger);
        }

    }

    @DeleteMapping(value = "/web/identifierUserAssignment/{id}")
    @Transactional
    public ResponseEntity voidIdentifierUserAssignment(@PathVariable("id") Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierUserAssignment);
        IdentifierUserAssignment identifierUserAssignment = identifierUserAssignmentRepository.findOne(id);
        if (identifierUserAssignment == null)
            return ResponseEntity.notFound().build();

        identifierUserAssignment.setVoided(true);
        identifierUserAssignmentRepository.save(identifierUserAssignment);
        return ResponseEntity.ok(null);
    }

    private IdentifierUserAssignment getIdentifierUserAssignment(IdentifierUserAssignmentContractWeb request) {
        IdentifierUserAssignment identifierUserAssignment = new IdentifierUserAssignment();
        identifierUserAssignment.setAssignedTo(request.getUserId() == null ? null : userRepository.findOne(request.getUserId()));
        identifierUserAssignment.setIdentifierSource(request.getIdentifierSourceId() == null ? null : identifierSourceRepository.findOne(request.getIdentifierSourceId()));
        identifierUserAssignment.setIdentifierStart(request.getIdentifierStart());
        identifierUserAssignment.setIdentifierEnd(request.getIdentifierEnd());
        identifierUserAssignment.setId(request.getId());
        identifierUserAssignment.setOrganisationId(request.getOrganisationId());
        return identifierUserAssignment;
    }
}

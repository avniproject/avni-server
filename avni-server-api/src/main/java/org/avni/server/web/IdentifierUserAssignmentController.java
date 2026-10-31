package org.avni.server.web;

import org.springframework.transaction.annotation.Transactional;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.identifier.IdentifierUserAssignmentService;
import org.avni.server.web.request.IdentifierUserAssignmentContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class IdentifierUserAssignmentController extends AbstractController<IdentifierUserAssignment> {
    private final IdentifierUserAssignmentRepository identifierUserAssignmentRepository;
    private final UserRepository userRepository;
    private final IdentifierSourceRepository identifierSourceRepository;
    private final AccessControlService accessControlService;
    private final IdentifierUserAssignmentService identifierUserAssignmentService;

    @Autowired
    public IdentifierUserAssignmentController(IdentifierUserAssignmentRepository identifierUserAssignmentRepository, UserRepository userRepository, IdentifierSourceRepository identifierSourceRepository, AccessControlService accessControlService, IdentifierUserAssignmentService identifierUserAssignmentService) {
        this.identifierUserAssignmentService = identifierUserAssignmentService;
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
        this.userRepository = userRepository;
        this.identifierSourceRepository = identifierSourceRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/identifierUserAssignments", method = RequestMethod.POST)
    @Transactional
    void save(@RequestBody List<IdentifierUserAssignmentContract> contracts) throws ValidationException {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierUserAssignment);
        for (IdentifierUserAssignmentContract contract : contracts) save(contract);
    }

    void save(IdentifierUserAssignmentContract contract) throws ValidationException {
        IdentifierUserAssignment identifierUserAssignment = newOrExistingEntity(identifierUserAssignmentRepository, contract, new IdentifierUserAssignment());
        IdentifierUserAssignment incoming = new IdentifierUserAssignment();
        incoming.setAssignedTo(userRepository.findByUuid(contract.getUserUUID()));
        incoming.setIdentifierSource(identifierSourceRepository.findByUuid(contract.getIdentifierSourceUUID()));
        incoming.setIdentifierStart(contract.getIdentifierStart());
        incoming.setIdentifierEnd(contract.getIdentifierEnd());
        identifierUserAssignmentService.assertNotModifiedAfterIssuance(identifierUserAssignment, incoming);

        identifierUserAssignment.setAssignedTo(incoming.getAssignedTo());
        identifierUserAssignment.setIdentifierSource(incoming.getIdentifierSource());
        identifierUserAssignment.setIdentifierStart(incoming.getIdentifierStart());
        identifierUserAssignment.setIdentifierEnd(incoming.getIdentifierEnd());
        identifierUserAssignment.setVoided(contract.isVoided());

        identifierUserAssignmentRepository.save(identifierUserAssignment);
    }
}

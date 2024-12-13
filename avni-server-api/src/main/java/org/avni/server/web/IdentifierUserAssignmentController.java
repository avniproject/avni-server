package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.IdentifierSourceRepository;
import org.avni.server.dao.IdentifierUserAssignmentRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.IdentifierUserAssignment;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
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

    @Autowired
    public IdentifierUserAssignmentController(IdentifierUserAssignmentRepository identifierUserAssignmentRepository, UserRepository userRepository, IdentifierSourceRepository identifierSourceRepository, AccessControlService accessControlService) {
        this.identifierUserAssignmentRepository = identifierUserAssignmentRepository;
        this.userRepository = userRepository;
        this.identifierSourceRepository = identifierSourceRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/identifierUserAssignments", method = RequestMethod.POST)
    @Transactional
    void save(@RequestBody List<IdentifierUserAssignmentContract> contracts) {
        accessControlService.checkPrivilege(PrivilegeType.EditIdentifierUserAssignment);
        contracts.forEach(this::save);
    }

    void save(IdentifierUserAssignmentContract contract) {
        IdentifierUserAssignment identifierUserAssignment = newOrExistingEntity(identifierUserAssignmentRepository, contract, new IdentifierUserAssignment());
        identifierUserAssignment.setAssignedTo(userRepository.findByUuid(contract.getUserUUID()));
        identifierUserAssignment.setIdentifierSource(identifierSourceRepository.findByUuid(contract.getIdentifierSourceUUID()));
        identifierUserAssignment.setIdentifierStart(contract.getIdentifierStart());
        identifierUserAssignment.setIdentifierEnd(contract.getIdentifierEnd());
        identifierUserAssignment.setVoided(contract.isVoided());

        identifierUserAssignmentRepository.save(identifierUserAssignment);
    }
}

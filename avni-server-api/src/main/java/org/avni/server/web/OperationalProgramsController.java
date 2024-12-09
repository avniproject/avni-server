package org.avni.server.web;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ProgramService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OperationalProgramsContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import jakarta.transaction.Transactional;

@RestController
public class OperationalProgramsController {
    private final ProgramService programService;
    private final AccessControlService accessControlService;

    @Autowired
    public OperationalProgramsController(ProgramService programService, AccessControlService accessControlService) {
        this.programService = programService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/operationalPrograms", method = RequestMethod.POST)
    @Transactional
    void saveOperationalPrograms(@RequestBody OperationalProgramsContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditProgram);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        request.getOperationalPrograms().forEach(operationalProgramContract -> {
            programService.createOperationalProgram(operationalProgramContract, organisation);
        });
    }
}

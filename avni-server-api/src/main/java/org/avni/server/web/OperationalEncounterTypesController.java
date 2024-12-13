package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.EncounterTypeService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OperationalEncounterTypesContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationalEncounterTypesController {
    private final EncounterTypeService encounterTypeService;
    private final AccessControlService accessControlService;

    @Autowired
    public OperationalEncounterTypesController(EncounterTypeService encounterTypeService, AccessControlService accessControlService) {
        this.encounterTypeService = encounterTypeService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/operationalEncounterTypes", method = RequestMethod.POST)
    @Transactional
    void saveOperationalEncounterTypes(@RequestBody OperationalEncounterTypesContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditEncounterType);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        request.getOperationalEncounterTypes().forEach(operationalEncounterTypeContract -> {
            encounterTypeService.createOperationalEncounterType(operationalEncounterTypeContract, organisation);
        });
    }
}

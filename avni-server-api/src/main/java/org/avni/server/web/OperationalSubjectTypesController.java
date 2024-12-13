package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.SubjectTypeService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.OperationalSubjectTypesContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OperationalSubjectTypesController {
    private final SubjectTypeService subjectTypeService;
    private final AccessControlService accessControlService;

    @Autowired
    public OperationalSubjectTypesController(SubjectTypeService subjectTypeService, AccessControlService accessControlService) {
        this.subjectTypeService = subjectTypeService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/operationalSubjectTypes", method = RequestMethod.POST)
    @Transactional
    void saveOperationalSubjectTypes(@RequestBody OperationalSubjectTypesContract request) {
        accessControlService.checkPrivilege(PrivilegeType.EditSubjectType);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        request.getOperationalSubjectTypes().forEach(operationalSubjectTypeContract -> {
            subjectTypeService.createOperationalSubjectType(operationalSubjectTypeContract, organisation);
        });
    }
}

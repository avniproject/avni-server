package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.ChecklistDetailService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.application.ChecklistDetailRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class ChecklistDetailController {
    private final ChecklistDetailService checklistDetailService;
    private final AccessControlService accessControlService;

    @Autowired
    public ChecklistDetailController(ChecklistDetailService checklistDetailService, AccessControlService accessControlService) {
        this.checklistDetailService = checklistDetailService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/checklistDetail", method = RequestMethod.POST)
    @Transactional
    public ResponseEntity<?> save(@RequestBody ChecklistDetailRequest checklistDetail) {
        accessControlService.checkPrivilege(PrivilegeType.EditChecklistConfiguration);
        checklistDetailService.saveChecklist(checklistDetail);
        return new ResponseEntity<>("Created", HttpStatus.CREATED);
    }

    @RequestMapping(value = "/web/checklistDetails", method = RequestMethod.GET)
    @Transactional
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public List<ChecklistDetailRequest> getChecklistDetails() {
        return checklistDetailService.getAllChecklistDetail();
    }

}

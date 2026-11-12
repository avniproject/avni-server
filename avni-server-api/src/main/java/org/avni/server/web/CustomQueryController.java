package org.avni.server.web;


import org.springframework.transaction.annotation.Transactional;
import org.avni.server.dao.QueryRepository;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.CustomQueryRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class CustomQueryController {

    private final QueryRepository queryRepository;
    private final AccessControlService accessControlService;

    @Autowired
    public CustomQueryController(QueryRepository queryRepository, AccessControlService accessControlService) {
        this.queryRepository = queryRepository;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/executeQuery", method = RequestMethod.POST)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @ResponseBody
    @Transactional
    public ResponseEntity<?> executeQuery(@RequestBody CustomQueryRequest customQueryRequest) {
        accessControlService.checkPrivilege(PrivilegeType.Analytics);
        if (customQueryRequest.getName() == null) {
            return ResponseEntity.badRequest().body("No query name passed in the request");
        }
        return queryRepository.runQuery(customQueryRequest);
    }
}

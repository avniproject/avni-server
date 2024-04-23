package org.avni.server.web.api;

import org.avni.server.dao.EntityApprovalStatusRepository;
import org.avni.server.dao.EntityApprovalStatusSearchParams;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.service.EntityApprovalStatusService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.response.EntityApprovalStatusResponse;
import org.avni.server.web.response.ResponsePage;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;

@RestController
public class EntityApprovalStatusApiController {

    private final EntityApprovalStatusRepository entityApprovalStatusRepository;
    private final EntityApprovalStatusService entityApprovalStatusService;
    private final AccessControlService accessControlService;

    @Autowired
    public EntityApprovalStatusApiController(EntityApprovalStatusRepository entityApprovalStatusRepository, EntityApprovalStatusService entityApprovalStatusService, AccessControlService accessControlService) {
        this.entityApprovalStatusRepository = entityApprovalStatusRepository;
        this.entityApprovalStatusService = entityApprovalStatusService;
        this.accessControlService = accessControlService;
    }

    @RequestMapping(value = "/api/approvalStatuses", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    public ResponsePage getEntityApprovalStatuses(@RequestParam(value = "lastModifiedDateTime", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                  @RequestParam(value = "now", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                  @RequestParam(value = "entityType", required = false) String entityType,
                                                  @RequestParam(value = "entityTypeId", required = false) String entityTypeUuid,
                                                  Pageable pageable) {

        Page<EntityApprovalStatus> entityApprovalStatuses = entityApprovalStatusRepository.findEntityApprovalStatuses(new EntityApprovalStatusSearchParams(lastModifiedDateTime, now, entityType, entityTypeUuid), pageable);
        ArrayList<EntityApprovalStatusResponse> entityApprovalStatusResponse = new ArrayList<>();
        entityApprovalStatuses.forEach(entityApprovalStatus -> entityApprovalStatusResponse.add(EntityApprovalStatusResponse.fromEntityApprovalStatus(entityApprovalStatus, entityApprovalStatusService.getEntityUuid(entityApprovalStatus))));
        accessControlService.checkApprovePrivilegeOnEntityApprovalStatuses(entityApprovalStatuses.getContent());
        return new ResponsePage(entityApprovalStatusResponse, entityApprovalStatuses.getNumberOfElements(), entityApprovalStatuses.getTotalPages(), entityApprovalStatuses.getSize());
    }
}

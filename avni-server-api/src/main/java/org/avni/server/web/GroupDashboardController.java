package org.avni.server.web;

import jakarta.transaction.Transactional;
import org.avni.server.dao.GroupDashboardRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.GroupDashboard;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.GroupDashboardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.DateTimeUtil;
import org.avni.server.web.request.GroupDashboardContract;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.avni.server.web.resourceProcessors.ResourceProcessor.addAuditFields;

@RestController
public class GroupDashboardController implements RestControllerResourceProcessor<GroupDashboard> {
    private final GroupDashboardRepository groupDashboardRepository;
    private final GroupDashboardService groupDashboardService;
    private final AccessControlService accessControlService;

    @Autowired
    public GroupDashboardController(GroupDashboardRepository groupDashboardRepository,
                                    GroupDashboardService groupDashboardService, AccessControlService accessControlService) {
        this.groupDashboardRepository = groupDashboardRepository;
        this.groupDashboardService = groupDashboardService;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/groupDashboard")
    @ResponseBody
    public List<GroupDashboardContract> getAll() {
        return groupDashboardRepository.findAllByIsVoidedFalse()
                .stream().map(GroupDashboardContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/groupDashboard/{id}")
    @ResponseBody
    public ResponseEntity<GroupDashboardContract> getById(@PathVariable Long id) {
        Optional<GroupDashboard> groupDashboard = groupDashboardRepository.findById(id);
        return groupDashboard.map(d -> ResponseEntity.ok(GroupDashboardContract.fromEntity(d)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/web/groupDashboard")
    @ResponseBody
    @Transactional
    public ResponseEntity saveGroupDashboards(@RequestBody List<GroupDashboardContract> request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        try {
            List<GroupDashboard> groupDashboards = groupDashboardService.save(request);
            return ResponseEntity.ok(groupDashboardRepository.saveAll(groupDashboards));
        } catch (ValidationException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping(value = "/web/groupDashboard/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<GroupDashboardContract> editGroupDashboard(@PathVariable Long id, @RequestBody GroupDashboardContract groupDashboardContract) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<GroupDashboard> groupDashboard = groupDashboardRepository.findById(id);
        if (!groupDashboard.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        GroupDashboard newGroupDashboard = groupDashboardService.edit(groupDashboardContract, id);
        return ResponseEntity.ok(GroupDashboardContract.fromEntity(newGroupDashboard));
    }

    @DeleteMapping(value = "/web/groupDashboard/{id}")
    @ResponseBody
    @Transactional
    public void deleteGroupDashboard(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<GroupDashboard> groupDashboard = groupDashboardRepository.findById(id);
        groupDashboard.ifPresent(groupDashboardService::delete);
    }

    @RequestMapping(value = "/groups/{id}/dashboards", method = RequestMethod.GET)
    public List<GroupDashboardContract> getDashboardsByGroupId(@PathVariable("id") Long id) {
        return groupDashboardRepository.findByGroup_IdAndIsVoidedFalseOrderByDashboardName(id).stream()
                .map(GroupDashboardContract::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/v2/groupDashboard/search/lastModified")
    public CollectionModel<EntityModel<GroupDashboard>> getDashboardFilters(@RequestParam("lastModifiedDateTime")
                                                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                         @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                                         Pageable pageable) {
        return wrap(groupDashboardRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(DateTimeUtil.toInstant(lastModifiedDateTime),
                CHSEntity.toDate(now), pageable));
    }

    @Override
    public EntityModel<GroupDashboard> process(EntityModel<GroupDashboard> resource) {
        GroupDashboard entity = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(entity.getDashboard().getUuid(), "dashboardUUID"));
        resource.add(Link.of(entity.getGroup().getUuid(), "groupUUID"));
        addAuditFields(entity, resource);
        return resource;
    }
}

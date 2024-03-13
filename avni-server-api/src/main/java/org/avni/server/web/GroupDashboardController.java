package org.avni.server.web;

import org.avni.server.dao.DashboardRepository;
import org.avni.server.dao.GroupDashboardRepository;
import org.avni.server.dao.GroupRepository;
import org.avni.server.domain.GroupDashboard;
import org.avni.server.domain.ValidationException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.GroupDashboardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.GroupDashboardContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class GroupDashboardController {
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
}

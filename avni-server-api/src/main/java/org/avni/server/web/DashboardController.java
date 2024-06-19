package org.avni.server.web;

import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.mapper.dashboard.DashboardMapper;
import org.avni.server.service.DashboardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.DashboardWebRequest;
import org.avni.server.web.response.reports.DashboardWebResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.transaction.Transactional;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
public class DashboardController implements RestControllerResourceProcessor<Dashboard> {
    private final DashboardRepository dashboardRepository;
    private final DashboardService dashboardService;
    private final DashboardMapper dashboardMapper;
    private final AccessControlService accessControlService;

    @Autowired
    public DashboardController(DashboardRepository dashboardRepository,
                               DashboardService dashboardService, DashboardMapper dashboardMapper, AccessControlService accessControlService) {
        this.dashboardRepository = dashboardRepository;
        this.dashboardService = dashboardService;
        this.dashboardMapper = dashboardMapper;
        this.accessControlService = accessControlService;
    }

    @GetMapping(value = "/web/dashboard")
    @ResponseBody
    public List<DashboardWebResponse> getAll() {
        return dashboardRepository.findAllByIsVoidedFalseOrderByName()
                .stream().map(dashboardMapper::toWebResponse)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/dashboard/{id}")
    @ResponseBody
    public DashboardWebResponse getById(@PathVariable Long id) {
        Dashboard dashboard = dashboardRepository.findEntity(id);
        return dashboardMapper.toWebResponse(dashboard);
    }

    @PostMapping(value = "/web/dashboard")
    @ResponseBody
    @Transactional
    public ResponseEntity<DashboardWebResponse> newDashboard(@RequestBody DashboardWebRequest dashboardRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Dashboard dashboard = dashboardService.saveDashboard(dashboardRequest);
        return ResponseEntity.ok(dashboardMapper.toWebResponse(dashboard));
    }

    @PutMapping(value = "/web/dashboard/{id}")
    @ResponseBody
    @Transactional
    public ResponseEntity<DashboardWebResponse> editDashboard(@PathVariable Long id, @RequestBody DashboardWebRequest dashboardRequest) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<Dashboard> dashboard = dashboardRepository.findById(id);
        if (!dashboard.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Dashboard newDashboard = dashboardService.editDashboard(dashboardRequest, id);
        return ResponseEntity.ok(dashboardMapper.toWebResponse(newDashboard));
    }

    @DeleteMapping(value = "/web/dashboard/{id}")
    @ResponseBody
    @Transactional
    public void deleteDashboard(@PathVariable Long id) {
        accessControlService.checkPrivilege(PrivilegeType.EditOfflineDashboardAndReportCard);
        Optional<Dashboard> dashboard = dashboardRepository.findById(id);
        dashboard.ifPresent(dashboardService::deleteDashboard);
    }

    //This is here because dashboardCardMapping used to get synced earlier which is no longer required now
    @Deprecated
    @RequestMapping(value = "/dashboardCardMapping/search/lastModified", method = RequestMethod.GET)
    public PagedResources<?> getByIndividualsOfCatchmentAndLastModified(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now) {
        return wrap(new PageImpl<>(Collections.emptyList()));
    }
}

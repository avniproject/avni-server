package org.avni.server.web;

import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.mapper.dashboard.DashboardMapper;
import org.avni.server.service.DashboardService;
import org.avni.server.web.request.DashboardRequest;
import org.avni.server.web.request.DashboardResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.PagedResources;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @Autowired
    public DashboardController(DashboardRepository dashboardRepository,
                               DashboardService dashboardService, DashboardMapper dashboardMapper) {
        this.dashboardRepository = dashboardRepository;
        this.dashboardService = dashboardService;
        this.dashboardMapper = dashboardMapper;
    }

    @GetMapping(value = "/web/dashboard")
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    @ResponseBody
    public List<DashboardResponse> getAll() {
        return dashboardRepository.findAllByIsVoidedFalse()
                .stream().map(dashboardMapper::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping(value = "/web/dashboard/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin','organisation_admin')")
    @ResponseBody
    public DashboardResponse getById(@PathVariable Long id) {
        Dashboard dashboard = dashboardRepository.findEntity(id);
        return dashboardMapper.fromEntity(dashboard);
    }

    @PostMapping(value = "/web/dashboard")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public ResponseEntity<DashboardResponse> newDashboard(@RequestBody DashboardRequest dashboardRequest) {
        Dashboard dashboard = dashboardService.saveDashboard(dashboardRequest);
        return ResponseEntity.ok(dashboardMapper.fromEntity(dashboard));
    }

    @PutMapping(value = "/web/dashboard/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public ResponseEntity<DashboardResponse> editDashboard(@PathVariable Long id, @RequestBody DashboardRequest dashboardRequest) {
        Optional<Dashboard> dashboard = dashboardRepository.findById(id);
        if (!dashboard.isPresent()) {
            return ResponseEntity.notFound().build();
        }
        Dashboard newDashboard = dashboardService.editDashboard(dashboardRequest, id);
        return ResponseEntity.ok(dashboardMapper.fromEntity(newDashboard));
    }

    @DeleteMapping(value = "/web/dashboard/{id}")
    @PreAuthorize(value = "hasAnyAuthority('admin', 'organisation_admin')")
    @ResponseBody
    @Transactional
    public void deleteDashboard(@PathVariable Long id) {
        Optional<Dashboard> dashboard = dashboardRepository.findById(id);
        dashboard.ifPresent(dashboardService::deleteDashboard);
    }

    //This is here because dashboardCardMapping used to get synced earlier which is no longer required now
    @Deprecated
    @RequestMapping(value = "/dashboardCardMapping/search/lastModified", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user', 'organisation_admin')")
    public PagedResources<?> getByIndividualsOfCatchmentAndLastModified(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
            @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now) {
        return wrap(new PageImpl<>(Collections.emptyList()));
    }
}

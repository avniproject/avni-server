package org.avni.server.web;

import org.avni.server.dao.DashboardRepository;
import org.avni.server.dao.OrganisationRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.domain.organisation.OrganisationCategory;
import org.avni.server.mapper.dashboard.DashboardMapper;
import org.avni.server.mapper.dashboard.DefaultDashboardConstants;
import org.avni.server.service.DashboardService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.DashboardWebRequest;
import org.avni.server.web.response.reports.DashboardWebResponse;
import org.avni.server.web.validation.ValidationException;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
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
    private final OrganisationRepository organisationRepository;

    @Autowired
    public DashboardController(DashboardRepository dashboardRepository,
                               DashboardService dashboardService, DashboardMapper dashboardMapper, AccessControlService accessControlService, OrganisationRepository organisationRepository) {
        this.dashboardRepository = dashboardRepository;
        this.dashboardService = dashboardService;
        this.dashboardMapper = dashboardMapper;
        this.accessControlService = accessControlService;
        this.organisationRepository = organisationRepository;
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


    @PostMapping(value = "/api/defaultDashboard/create")
    @ResponseBody
    @Transactional
    public ResponseEntity<DashboardWebResponse> createDefaultDashboard(@RequestParam(value = "orgId") Long orgId) {
        accessControlService.assertIsSuperAdmin();
        Organisation organisation = organisationRepository.findById(orgId).orElse(null);
        if (organisation == null) {
            throw new ValidationException(String.format("Organisation with id: %d does not exist", orgId));
        }
        if (OrganisationCategory.Production.equals(organisation.getCategory().getName())) {
            throw new ValidationException("Cannot create default dashboard on Prod org. Create default dashboard in UAT org first, test and then upload bundle to prod.");
        }
        if (dashboardRepository.findByUuidAndOrganisationIdAndIsVoidedFalse(DefaultDashboardConstants.DASHBOARD_NAME_UUID_MAPPING.get(DefaultDashboardConstants.DEFAULT_DASHBOARD), orgId) != null) {
            throw new ValidationException("Default dashboard already exists.");
        }
        return ResponseEntity.ok(dashboardMapper.toWebResponse(dashboardService.createDefaultDashboard(organisation)));
    }

    //This is here because dashboardCardMapping used to get synced earlier which is no longer required now
    @Deprecated
    @RequestMapping(value = "/dashboardCardMapping/search/lastModified", method = RequestMethod.GET)
    public PagedResources<?> getByIndividualsOfCatchmentAndLastModified(@RequestParam("lastModifiedDateTime") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                                        @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now) {
        return wrap(new PageImpl<>(Collections.emptyList()));
    }

    @GetMapping(value = "/v2/dashboard/search/lastModified")
    public PagedResources<Resource<Dashboard>> getDashboards(@RequestParam("lastModifiedDateTime")
                                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime lastModifiedDateTime,
                                                             @RequestParam("now") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) DateTime now,
                                                             Pageable pageable) {
        return wrap(dashboardRepository.findByLastModifiedDateTimeIsGreaterThanEqualAndLastModifiedDateTimeLessThanEqualOrderByLastModifiedDateTimeAscIdAsc(lastModifiedDateTime.toDate(),
                CHSEntity.toDate(now), pageable));
    }
}

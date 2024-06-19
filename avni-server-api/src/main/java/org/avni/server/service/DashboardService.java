package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionCardMappingBundleContract;
import org.avni.server.web.request.*;
import org.avni.server.web.request.reports.DashboardSectionCardMappingRequest;
import org.avni.server.web.request.reports.DashboardSectionWebRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.joda.time.DateTime;

import java.util.*;

@Service
public class DashboardService implements NonScopeAwareService {
    private final DashboardRepository dashboardRepository;
    private final CardRepository cardRepository;
    private final DashboardSectionRepository dashboardSectionRepository;
    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;
    private final DashboardFilterRepository dashboardFilterRepository;

    @Autowired
    public DashboardService(DashboardRepository dashboardRepository,
                            CardRepository cardRepository,
                            DashboardSectionRepository dashboardSectionRepository, DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository, DashboardFilterRepository dashboardFilterRepository) {
        this.dashboardRepository = dashboardRepository;
        this.cardRepository = cardRepository;
        this.dashboardSectionRepository = dashboardSectionRepository;
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
        this.dashboardFilterRepository = dashboardFilterRepository;
    }

    public Dashboard saveDashboard(DashboardWebRequest dashboardRequest) {
        assertNoExistingDashboardWithName(dashboardRequest.getName());
        Dashboard dashboard = new Dashboard();
        dashboard.assignUUID();
        return buildDashboard(dashboardRequest, dashboard);
    }

    public void uploadDashboard(DashboardBundleContract dashboardContract) {
        Dashboard dashboard = dashboardRepository.findByUuid(dashboardContract.getUuid());
        if (dashboard == null) {
            dashboard = new Dashboard();
            dashboard.setUuid(dashboardContract.getUuid());
        }
        dashboard.setName(dashboardContract.getName());
        dashboard.setDescription(dashboardContract.getDescription());
        dashboard.setVoided(dashboardContract.isVoided());
        Dashboard savedDashboard = dashboardRepository.save(dashboard);
        uploadDashboardSections(dashboardContract, savedDashboard);
    }

    private void uploadDashboardSections(DashboardBundleContract dashboardContract, Dashboard dashboard) {
        for (DashboardSectionBundleContract sectionContract : dashboardContract.getSections()) {
            DashboardSection section = dashboardSectionRepository.findByUuid(sectionContract.getUuid());
            if (section == null) {
                section = new DashboardSection();
                section.setUuid(sectionContract.getUuid());
            }
            section.setDashboard(dashboard);
            section.setName(sectionContract.getName());
            section.setDescription(sectionContract.getDescription());
            section.setViewType(DashboardSection.ViewType.valueOf(sectionContract.getViewType()));
            section.setDisplayOrder(sectionContract.getDisplayOrder());
            section.setVoided(sectionContract.isVoided());
            DashboardSection savedSection = dashboardSectionRepository.save(section);

            for (DashboardSectionCardMappingBundleContract sectionCardMappingContract : sectionContract.getDashboardSectionCardMappings()) {
                DashboardSectionCardMapping mapping = dashboardSectionCardMappingRepository.findByUuid(sectionCardMappingContract.getUuid());
                if (mapping == null) {
                    mapping = new DashboardSectionCardMapping();
                    mapping.setUuid(sectionCardMappingContract.getUuid());
                }
                mapping.setDashboardSection(savedSection);
                mapping.setCard(cardRepository.findByUuid(sectionCardMappingContract.getReportCardUUID()));
                mapping.setDisplayOrder(sectionCardMappingContract.getDisplayOrder());
                mapping.setVoided(sectionCardMappingContract.isVoided());
                dashboardSectionCardMappingRepository.save(mapping);
            }
        }
    }

    public Dashboard editDashboard(DashboardWebRequest dashboardRequest, Long dashboardId) {
        Dashboard existingDashboard = dashboardRepository.findOne(dashboardId);
        assertNewNameIsUnique(dashboardRequest.getName(), existingDashboard.getName());
        return buildDashboard(dashboardRequest, existingDashboard);
    }

    public void deleteDashboard(Dashboard dashboard) {
        dashboard.setVoided(true);
        dashboardRepository.save(dashboard);
    }

    private Dashboard buildDashboard(DashboardWebRequest dashboardRequest, Dashboard dashboard) {
        dashboard.setName(dashboardRequest.getName());
        dashboard.setDescription(dashboardRequest.getDescription());
        dashboard.setVoided(dashboardRequest.isVoided());
        dashboardRepository.save(dashboard);
        setDashboardSections(dashboardRequest, dashboard);
        setDashboardFilters(dashboardRequest, dashboard);
        return dashboardRepository.save(dashboard);
    }

    private void setDashboardSections(DashboardWebRequest dashboardRequest, Dashboard dashboard) {
        List<DashboardSectionWebRequest> sectionRequests = dashboardRequest.getSections();
        for (DashboardSectionWebRequest sectionRequest : sectionRequests) {
            DashboardSection section = dashboard.getSection(sectionRequest.getUuid());
            if (section == null) {
                section = new DashboardSection();
                section.assignUUID();
            }
            section.setName(sectionRequest.getName());
            section.setDescription(sectionRequest.getDescription());
            section.setViewType(DashboardSection.ViewType.valueOf(sectionRequest.getViewType()));
            section.setDisplayOrder(sectionRequest.getDisplayOrder());
            section.setVoided(sectionRequest.isVoided());
            dashboard.addSection(section);

            List<DashboardSectionCardMappingRequest> sectionCardMappingRequests = sectionRequest.getDashboardSectionCardMappings();
            for (DashboardSectionCardMappingRequest sectionCardMappingRequest : sectionCardMappingRequests) {
                DashboardSectionCardMapping mapping = section.getDashboardSectionMapping(sectionCardMappingRequest.getUuid());
                if (mapping == null) {
                    mapping = new DashboardSectionCardMapping();
                    mapping.assignUUID();
                }
                mapping.setDashboardSection(section);
                mapping.setDisplayOrder(sectionCardMappingRequest.getDisplayOrder());
                mapping.setVoided(sectionCardMappingRequest.isVoided());
                mapping.setCard(cardRepository.findByUuid(sectionCardMappingRequest.getReportCardUUID()));
                section.addDashboardSectionCardMapping(mapping);
            }
        }
    }

    private void setDashboardFilters(DashboardWebRequest dashboardRequest, Dashboard dashboard) {
        Set<DashboardFilter> existingFilters = dashboard.getDashboardFilters();
        List<String> existingFilterUuids = new ArrayList<>();
        for (DashboardFilter existingFilter : existingFilters) {
            if (!existingFilter.isVoided()) existingFilterUuids.add(existingFilter.getUuid());
        }
        List<DashboardFilterRequest> filterRequests = dashboardRequest.getFilters();
        for (DashboardFilterRequest filterRequest : filterRequests) {
            DashboardFilter dashboardFilter = dashboardFilterRepository.findByUuid(filterRequest.getUuid());
            existingFilterUuids.remove(filterRequest.getUuid());
            if (dashboardFilter == null) {
                dashboardFilter = new DashboardFilter();
            }
            dashboardFilter.assignUUIDIfRequired();
            dashboardFilter.setName(filterRequest.getName());
            dashboardFilter.setFilterConfig(filterRequest.getFilterConfig().toJsonObject());
            dashboard.addUpdateFilter(dashboardFilter);
        }
        for (String existingFilterUuid : existingFilterUuids) {
            DashboardFilter dashboardFilter = dashboardFilterRepository.findByUuid(existingFilterUuid);
            dashboardFilter.setVoided(true);
            dashboard.addUpdateFilter(dashboardFilter);
        }
    }

    private void assertNewNameIsUnique(String newName, String oldName) {
        if (!newName.equals(oldName)) {
            assertNoExistingDashboardWithName(newName);
        }
    }

    private void assertNoExistingDashboardWithName(String name) {
        Dashboard existingDashboard = dashboardRepository.findByName(name);
        if (existingDashboard != null) {
            throw new BadRequestError(String.format("Dashboard %s already exists", name));
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return dashboardRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}

package org.avni.server.service;

import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionCardMappingBundleContract;
import org.avni.server.web.request.DashboardFilterRequest;
import org.avni.server.web.request.DashboardFilterResponse;
import org.avni.server.web.request.DashboardWebRequest;
import org.avni.server.web.request.reports.DashboardSectionCardMappingRequest;
import org.avni.server.web.request.reports.DashboardSectionWebRequest;
import org.avni.server.web.response.reports.DashboardFilterConfigResponse;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class DashboardService implements NonScopeAwareService {
    private final DashboardRepository dashboardRepository;
    private final CardRepository cardRepository;
    private final DashboardSectionRepository dashboardSectionRepository;
    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;
    private final DashboardFilterRepository dashboardFilterRepository;
    private final CardService cardService;

    @Autowired
    public DashboardService(DashboardRepository dashboardRepository,
                            CardRepository cardRepository,
                            DashboardSectionRepository dashboardSectionRepository, DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository, DashboardFilterRepository dashboardFilterRepository, CardService cardService) {
        this.dashboardRepository = dashboardRepository;
        this.cardRepository = cardRepository;
        this.dashboardSectionRepository = dashboardSectionRepository;
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
        this.dashboardFilterRepository = dashboardFilterRepository;
        this.cardService = cardService;
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
        uploadDashboardFilters(dashboardContract, savedDashboard);
    }

    private void uploadDashboardFilters(DashboardBundleContract bundleContract, Dashboard dashboard) {
        List<DashboardFilterResponse> filters = bundleContract.getFilters();
        for (DashboardFilterResponse bundleFilter : filters) {
            DashboardFilter dashboardFilter = dashboardFilterRepository.findByUuid(bundleFilter.getUuid());
            if (dashboardFilter == null) {
                dashboardFilter = new DashboardFilter();
                dashboardFilter.setUuid(bundleFilter.getUuid());
            }
            dashboardFilter.setName(bundleFilter.getName());
            DashboardFilterConfigResponse bundleFilterConfig = bundleFilter.getFilterConfig();
            dashboardFilter.setFilterConfig(bundleFilterConfig.toJsonObject());
            dashboard.addUpdateFilter(dashboardFilter);
        }
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
        dashboard.setName((ReactAdminUtil.getVoidedName(dashboard.getName(), dashboard.getId())));
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

    public void saveDashboards(DashboardBundleContract[] dashboardContracts) {
        for (DashboardBundleContract dashboardContract : dashboardContracts) {
            try {
                uploadDashboard(dashboardContract);
            } catch (Exception e) {
                throw new BulkItemSaveException(dashboardContract, e);
            }
        }
    }

    public Dashboard createDefaultDashboard(Organisation organisation) {
        Map<String, ReportCard> defaultDashboardCards = cardService.createDefaultDashboardCards(organisation);
        Dashboard defaultDashboard = createDashboard(organisation, "Default Dashboard");

        DashboardSection visitDetailsSection = createDashboardSection(organisation, "Visit Details", 1.0);
        DashboardSectionCardMapping scheduledVisitsToVisitDetailsMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Scheduled visits"), visitDetailsSection, 1.0);
        visitDetailsSection.addDashboardSectionCardMapping(scheduledVisitsToVisitDetailsMapping);
        DashboardSectionCardMapping overdueVisitsToVisitDetailsMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Overdue visits"), visitDetailsSection, 2.0);
        visitDetailsSection.addDashboardSectionCardMapping(overdueVisitsToVisitDetailsMapping);
        defaultDashboard.addSection(visitDetailsSection);

        DashboardSection recentStatisticsSection = createDashboardSection(organisation, "Recent Statistics", 2.0);
        DashboardSectionCardMapping recentRegistrationsToStatisticsMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Recent registrations"), recentStatisticsSection, 1.0);
        recentStatisticsSection.addDashboardSectionCardMapping(recentRegistrationsToStatisticsMapping);
        DashboardSectionCardMapping recentEnrolmentsToStatisticsMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Recent enrolments"), recentStatisticsSection, 2.0);
        recentStatisticsSection.addDashboardSectionCardMapping(recentEnrolmentsToStatisticsMapping);
        DashboardSectionCardMapping recentVisitsToStatisticsMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Recent visits"), recentStatisticsSection, 3.0);
        recentStatisticsSection.addDashboardSectionCardMapping(recentVisitsToStatisticsMapping);
        defaultDashboard.addSection(recentStatisticsSection);

        DashboardSection registrationOverviewSection = createDashboardSection(organisation, "Registration Overview", 3.0);
        DashboardSectionCardMapping totalToRegistrationOverviewMapping = createDashboardSectionCardMapping(organisation, defaultDashboardCards.get("Total"), registrationOverviewSection, 1.0);
        registrationOverviewSection.addDashboardSectionCardMapping(totalToRegistrationOverviewMapping);
        defaultDashboard.addSection(registrationOverviewSection);

        DashboardFilter subjectTypeFilter = createDashboardFilter(organisation, "Subject Type", new JsonObject()
                .with(DashboardFilter.DashboardFilterConfig.TypeFieldName, String.valueOf(DashboardFilter.FilterType.SubjectType)));
        defaultDashboard.addUpdateFilter(subjectTypeFilter);

        DashboardFilter asOnDateFilter = createDashboardFilter(organisation, "As On Date", new JsonObject()
                .with(DashboardFilter.DashboardFilterConfig.TypeFieldName, String.valueOf(DashboardFilter.FilterType.AsOnDate)));
        defaultDashboard.addUpdateFilter(asOnDateFilter);

        return dashboardRepository.save(defaultDashboard);
    }

    private OrganisationAwareEntity setDefaults(OrganisationAwareEntity entity, Organisation organisation) {
        entity.assignUUID();
        entity.setOrganisationId(organisation.getId());
        return entity;
    }

    private Dashboard createDashboard(Organisation organisation, String name) {
        Dashboard dashboard = new Dashboard();
        setDefaults(dashboard, organisation);
        dashboard.setName(name);
        return dashboard;
    }

    private DashboardSection createDashboardSection(Organisation organisation, String name, Double displayOrder) {
        DashboardSection dashboardSection = new DashboardSection();
        setDefaults(dashboardSection, organisation);
        dashboardSection.setName(name);
        dashboardSection.setViewType(DashboardSection.ViewType.Default);
        dashboardSection.setDisplayOrder(displayOrder);
        return dashboardSection;
    }

    private DashboardSectionCardMapping createDashboardSectionCardMapping(Organisation organisation, ReportCard reportCard, DashboardSection dashboardSection, Double displayOrder) {
        DashboardSectionCardMapping dashboardSectionCardMapping = new DashboardSectionCardMapping();
        setDefaults(dashboardSectionCardMapping, organisation);
        dashboardSectionCardMapping.setCard(reportCard);
        dashboardSectionCardMapping.setDashboardSection(dashboardSection);
        dashboardSectionCardMapping.setDisplayOrder(displayOrder);
        return dashboardSectionCardMapping;
    }

    private DashboardFilter createDashboardFilter(Organisation organisation, String name, JsonObject filterConfig) {
        DashboardFilter dashboardFilter = new DashboardFilter();
        setDefaults(dashboardFilter, organisation);
        dashboardFilter.setName(name);
        dashboardFilter.setFilterConfig(filterConfig);
        return dashboardFilter;
    }
}

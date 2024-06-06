package org.avni.server.service;

import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.mapper.dashboard.DashboardMapper;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.joda.time.DateTime;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService implements NonScopeAwareService {

    private final DashboardRepository dashboardRepository;
    private final CardRepository cardRepository;
    private final DashboardSectionRepository dashboardSectionRepository;
    private final DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository;
    private final DashboardFilterRepository dashboardFilterRepository;
    private final DashboardMapper dashboardMapper;

    @Autowired
    public DashboardService(DashboardRepository dashboardRepository,
                            CardRepository cardRepository,
                            DashboardSectionRepository dashboardSectionRepository, DashboardSectionCardMappingRepository dashboardSectionCardMappingRepository, DashboardFilterRepository dashboardFilterRepository, DashboardMapper dashboardMapper) {
        this.dashboardRepository = dashboardRepository;
        this.cardRepository = cardRepository;
        this.dashboardSectionRepository = dashboardSectionRepository;
        this.dashboardSectionCardMappingRepository = dashboardSectionCardMappingRepository;
        this.dashboardFilterRepository = dashboardFilterRepository;
        this.dashboardMapper = dashboardMapper;
    }

    public Dashboard saveDashboard(DashboardRequest dashboardRequest) {
        assertNoExistingDashboardWithName(dashboardRequest.getName());
        Dashboard dashboard = new Dashboard();
        dashboard.assignUUID();
        return buildDashboard(dashboardRequest, dashboard);
    }

    public void uploadDashboard(DashboardResponse dashboardContract) {
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

    private void uploadDashboardSections(DashboardResponse dashboardContract, Dashboard dashboard) {
        for (DashboardSectionContract sectionContract : dashboardContract.getSections()) {
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

            for (DashboardSectionCardMappingContract sectionCardMappingContract : sectionContract.getDashboardSectionCardMappings()) {
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

    public Dashboard editDashboard(DashboardRequest dashboardRequest, Long dashboardId) {
        Dashboard existingDashboard = dashboardRepository.findOne(dashboardId);
        assertNewNameIsUnique(dashboardRequest.getName(), existingDashboard.getName());
        return buildDashboard(dashboardRequest, existingDashboard);
    }

    public void deleteDashboard(Dashboard dashboard) {
        dashboard.setVoided(true);
        dashboardRepository.save(dashboard);
    }

    public List<DashboardResponse> getAll() {
        List<Dashboard> dashboards = dashboardRepository.findAll();
        return dashboards.stream().map(dashboardMapper::fromEntity).collect(Collectors.toList());
    }

    private Dashboard buildDashboard(DashboardRequest dashboardRequest, Dashboard dashboard) {
        dashboard.setName(dashboardRequest.getName());
        dashboard.setDescription(dashboardRequest.getDescription());
        dashboard.setVoided(dashboardRequest.isVoided());
        dashboardRepository.save(dashboard);
        setDashboardSections(dashboardRequest, dashboard);
        setDashboardFilters(dashboardRequest, dashboard);
        return dashboardRepository.save(dashboard);
    }

    private void setDashboardSections(DashboardRequest dashboardRequest, Dashboard dashboard) {
        Set<DashboardSection> dashboardSections = new HashSet<>();
        List<DashboardSectionContract> sectionContracts = dashboardRequest.getSections();
        for (DashboardSectionContract sectionContract : sectionContracts) {
            Long sectionId = sectionContract.getId();
            DashboardSection section;
            if (sectionId != null) {
                section = dashboardSectionRepository.findOne(sectionContract.getId());
            } else {
                section = new DashboardSection();
                section.assignUUID();
            }
            section.setDashboard(dashboard);
            section.setName(sectionContract.getName());
            section.setDescription(sectionContract.getDescription());
            section.setViewType(DashboardSection.ViewType.valueOf(sectionContract.getViewType()));
            section.setDisplayOrder(sectionContract.getDisplayOrder());
            section = dashboardSectionRepository.save(section);

            List<ReportCardContract> cardContracts = sectionContract.getCards();
            Set<DashboardSectionCardMapping> updatedMappings = new HashSet<>();
            for (ReportCardContract cardContract : cardContracts) {
                DashboardSectionCardMapping mapping = dashboardSectionCardMappingRepository.findByCardIdAndDashboardSectionAndIsVoidedFalse(cardContract.getId(), section);
                if (mapping == null) {
                    mapping = new DashboardSectionCardMapping();
                    mapping.assignUUID();
                    mapping.setDashboardSection(section);
                    mapping.setCard(cardRepository.findOne(cardContract.getId()));
                }
                mapping.setDisplayOrder(cardContract.getDisplayOrder());
                updatedMappings.add(mapping);
            }
            Set<DashboardSectionCardMapping> savedMappings = section.getDashboardSectionCardMappings();
            voidOldMappings(updatedMappings, savedMappings);
            section.setDashboardSectionCardMappings(updatedMappings);
            dashboardSectionCardMappingRepository.saveAll(updatedMappings);
            dashboardSections.add(section);
        }
        dashboard.setDashboardSections(dashboardSections);
    }

    private void setDashboardFilters(DashboardRequest dashboardRequest, Dashboard dashboard) {
        Set<DashboardFilter> existingFilters = dashboard.getDashboardFilters();
        List<String> existingFilterUuids = new ArrayList<>();
        for (DashboardFilter existingFilter: existingFilters) {
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

    private void voidOldMappings(Set<DashboardSectionCardMapping> newMappings, Set<DashboardSectionCardMapping> savedMappings) {
        Set<Long> updatedMappingIds = newMappings.stream()
                .map(CHSBaseEntity::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        for (DashboardSectionCardMapping savedMapping : savedMappings) {
            if (!updatedMappingIds.contains(savedMapping.getId())) {
                DashboardSectionCardMapping dashboardCardMapping = dashboardSectionCardMappingRepository.findOne(savedMapping.getId());
                dashboardCardMapping.setVoided(true);
            }
        }
    }

    private void assertNewNameIsUnique(String newName, String oldName) {
        if (!newName.equals(oldName)) {
            assertNoExistingDashboardWithName(newName);
        }
    }

    private void assertNoExistingDashboardWithName(String name) {
        boolean existingDashboard = dashboardRepository.existsByNameAndIsVoidedFalse(name);
        if (existingDashboard == false) {
            throw new BadRequestError(String.format("Dashboard %s already exists", name));
        }
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return dashboardRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}

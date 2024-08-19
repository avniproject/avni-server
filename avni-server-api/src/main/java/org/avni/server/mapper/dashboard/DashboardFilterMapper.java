package org.avni.server.mapper.dashboard;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.avni.server.web.contract.reports.DashboardFilterBundleContract;
import org.avni.server.web.contract.reports.DashboardFilterContract;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.DashboardFilterResponse;
import org.avni.server.web.response.reports.DashboardFilterConfigResponse;
import org.avni.server.web.response.reports.ObservationBasedFilterResponse;
import org.springframework.stereotype.Component;

@Component
public class DashboardFilterMapper {
    private final ConceptRepository conceptRepository;

    public DashboardFilterMapper(ConceptRepository conceptRepository) {
        this.conceptRepository = conceptRepository;
    }

    public DashboardFilterBundleContract toBundle(DashboardFilter dashboardFilter) {
        return (DashboardFilterBundleContract) toContract(dashboardFilter, new DashboardFilterBundleContract());
    }

    private DashboardFilterContract toContract(DashboardFilter dashboardFilter, DashboardFilterContract filterContract) {
        filterContract.setId(dashboardFilter.getId());
        filterContract.setUuid(dashboardFilter.getUuid());
        filterContract.setVoided(dashboardFilter.isVoided());
        filterContract.setName(dashboardFilter.getName());

        DashboardFilterConfigContract filterConfigContract = filterContract.newFilterConfig();
        DashboardFilter.DashboardFilterConfig filterConfig = dashboardFilter.getFilterConfig();
        filterConfigContract.setSubjectTypeUUID(filterConfig.getSubjectTypeUuid());
        filterConfigContract.setWidget(filterConfig.getWidget());
        filterConfigContract.setType(filterConfig.getType().name());

        DashboardFilter.FilterType filterType = dashboardFilter.getFilterConfig().getType();
        if (filterType.equals(DashboardFilter.FilterType.GroupSubject)) {
            DashboardFilter.GroupSubjectTypeFilter groupSubjectTypeFilter = filterConfig.getGroupSubjectTypeFilter();
            DashboardFilterConfigContract.GroupSubjectTypeFilterContract groupSubjectTypeFilterContract = new DashboardFilterConfigContract.GroupSubjectTypeFilterContract();
            groupSubjectTypeFilterContract.setSubjectTypeUUID(groupSubjectTypeFilter.getSubjectTypeUUID());
            filterConfigContract.setGroupSubjectTypeFilter(groupSubjectTypeFilterContract);
        } else if (filterType.equals(DashboardFilter.FilterType.Concept)) {
            DashboardFilter.ObservationBasedFilter observationBasedFilter = filterConfig.getObservationBasedFilter();
            ObservationBasedFilterContract observationBasedFilterContract = filterConfigContract.newObservationBasedFilter();

            Concept concept = conceptRepository.findByUuid(observationBasedFilter.getConcept());
            ConceptContract conceptContract = new ConceptContract();
            conceptContract.setUuid(concept.getUuid());
            conceptContract.setName(concept.getName());
            conceptContract.setDataType(concept.getDataType());
            observationBasedFilterContract.setConcept(conceptContract);

            observationBasedFilterContract.setScope(observationBasedFilter.getScope());
            observationBasedFilterContract.setProgramUUIDs(observationBasedFilter.getPrograms());
            observationBasedFilterContract.setEncounterTypeUUIDs(observationBasedFilter.getEncounterTypes());
        }
        return filterContract;
    }

    public DashboardFilterResponse toResponse(DashboardFilter dashboardFilter) {
        return (DashboardFilterResponse) toContract(dashboardFilter, new DashboardFilterResponse());
    }
}

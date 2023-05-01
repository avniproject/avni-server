package org.avni.server.mapper.dashboard;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.DashboardFilterConfigContract;
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

    public DashboardFilterResponse fromEntity(DashboardFilter df) {
        DashboardFilterResponse dashboardFilterResponse = new DashboardFilterResponse();
        dashboardFilterResponse.setId(df.getId());
        dashboardFilterResponse.setUuid(df.getUuid());
        dashboardFilterResponse.setVoided(df.isVoided());
        dashboardFilterResponse.setName(df.getName());

        DashboardFilterConfigResponse filterConfigResponse = new DashboardFilterConfigResponse();
        DashboardFilter.DashboardFilterConfig filterConfig = df.getFilterConfig();
        filterConfigResponse.setSubjectTypeUUID(filterConfig.getSubjectTypeUuid());
        filterConfigResponse.setWidget(filterConfig.getWidget());
        filterConfigResponse.setType(filterConfig.getType().name());
        dashboardFilterResponse.setFilterConfig(filterConfigResponse);

        DashboardFilter.FilterType filterType = df.getFilterConfig().getType();
        if (filterType.equals(DashboardFilter.FilterType.GroupSubject)) {
            DashboardFilter.GroupSubjectTypeFilter groupSubjectTypeFilter = filterConfig.getGroupSubjectTypeFilter();
            DashboardFilterConfigContract.GroupSubjectTypeFilterContract groupSubjectTypeFilterContract = new DashboardFilterConfigContract.GroupSubjectTypeFilterContract();
            groupSubjectTypeFilterContract.setSubjectTypeUUID(groupSubjectTypeFilter.getSubjectTypeUUID());
            filterConfigResponse.setGroupSubjectTypeFilter(groupSubjectTypeFilterContract);
        } else if (filterType.equals(DashboardFilter.FilterType.Concept)) {
            DashboardFilter.ObservationBasedFilter observationBasedFilter = filterConfig.getObservationBasedFilter();
            ObservationBasedFilterResponse observationBasedFilterResponse = new ObservationBasedFilterResponse();

            Concept concept = conceptRepository.findByUuid(observationBasedFilter.getConcept());
            ConceptContract conceptContract = new ConceptContract();
            conceptContract.setUuid(concept.getUuid());
            conceptContract.setName(concept.getName());
            conceptContract.setDataType(concept.getDataType());
            observationBasedFilterResponse.setConcept(conceptContract);

            observationBasedFilterResponse.setScope(observationBasedFilter.getScope());
            observationBasedFilterResponse.setProgramUUIDs(observationBasedFilter.getPrograms());
            observationBasedFilterResponse.setEncounterTypeUUIDs(observationBasedFilter.getEncounterTypes());
            filterConfigResponse.setObservationBasedFilter(observationBasedFilterResponse);
        }
        return dashboardFilterResponse;
    }
}

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
            DashboardFilter.GroupSubjectTypeFilter groupSubjectTypeScope = filterConfig.getGroupSubjectTypeFilter();
            DashboardFilterConfigContract.GroupSubjectTypeFilterContract groupSubjectTypeScopeContract = new DashboardFilterConfigContract.GroupSubjectTypeFilterContract();
            groupSubjectTypeScopeContract.setSubjectTypeUUID(groupSubjectTypeScope.getSubjectTypeUUID());
            filterConfigResponse.setGroupSubjectTypeScope(groupSubjectTypeScopeContract);
        } else if (filterType.equals(DashboardFilter.FilterType.Concept)) {
            DashboardFilter.ObservationBasedFilter conceptScope = filterConfig.getObservationBasedFilter();
            ObservationBasedFilterResponse conceptScopeContract = new ObservationBasedFilterResponse();

            Concept concept = conceptRepository.findByUuid(conceptScope.getConcept());
            ConceptContract conceptContract = new ConceptContract();
            conceptContract.setUuid(concept.getUuid());
            conceptContract.setName(concept.getName());
            conceptContract.setDataType(concept.getDataType());
            conceptScopeContract.setConcept(conceptContract);

            conceptScopeContract.setProgramUUIDs(conceptScope.getPrograms());
            conceptScopeContract.setEncounterTypeUUIDs(conceptScope.getEncounterTypes());
        }
        return dashboardFilterResponse;
    }
}

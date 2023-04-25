package org.avni.server.mapper.dashboard;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.EncounterTypeRepository;
import org.avni.server.dao.ProgramRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.DashboardFilterConfigResponse;
import org.avni.server.web.request.DashboardFilterContract;
import org.avni.server.web.request.ReferenceDataContract;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class DashboardFilterMapper {
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final ConceptRepository conceptRepository;

    public DashboardFilterMapper(SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository,
                                 ConceptRepository conceptRepository) {
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.conceptRepository = conceptRepository;
    }

    public DashboardFilterContract fromEntity(DashboardFilter df) {
        DashboardFilterContract dashboardFilterContract = new DashboardFilterContract();
        dashboardFilterContract.setId(df.getId());
        dashboardFilterContract.setUuid(df.getUuid());
        dashboardFilterContract.setVoided(df.isVoided());
        dashboardFilterContract.setName(df.getName());

        DashboardFilterConfigResponse filterConfigContract = new DashboardFilterConfigResponse();
        DashboardFilter.DashboardFilterConfig filterConfig = df.getFilterConfig();
        filterConfigContract.setWidget(filterConfig.getWidget());
        filterConfigContract.setType(filterConfig.getConfigType());
        filterConfigContract.setTypeTitle(filterConfig.getConfigType().getTitle());
        dashboardFilterContract.setConfig(filterConfigContract);

        DashboardFilter.FilterType configType = df.getFilterConfig().getConfigType();
        if (configType.equals(DashboardFilter.FilterType.GroupSubject)) {
            DashboardFilter.GroupSubjectTypeScope groupSubjectTypeScope = filterConfig.getGroupSubjectTypeScope();
            DashboardFilterConfigResponse.GroupSubjectTypeScopeResponse groupSubjectTypeScopeContract = new DashboardFilterConfigResponse.GroupSubjectTypeScopeResponse();
            SubjectType subjectType = subjectTypeRepository.findByUuid(groupSubjectTypeScope.getSubjectType());
            groupSubjectTypeScopeContract.setSubjectType(new ReferenceDataContract(subjectType));
            filterConfigContract.setGroupSubjectTypeScope(groupSubjectTypeScopeContract);
        } else if (configType.equals(DashboardFilter.FilterType.Concept)) {
            DashboardFilter.ConceptScope conceptScope = filterConfig.getConceptScope();
            DashboardFilterConfigResponse.ConceptScopeResponse conceptScopeContract = new DashboardFilterConfigResponse.ConceptScopeResponse();

            Concept concept = conceptRepository.findByUuid(conceptScope.getConcept());
            ConceptContract conceptContract = new ConceptContract();
            conceptContract.setUuid(concept.getUuid());
            conceptContract.setName(concept.getName());
            conceptContract.setDataType(concept.getDataType());
            conceptScopeContract.setConcept(conceptContract);

            conceptScopeContract.setPrograms(conceptScope.getPrograms().stream().map(s -> new ReferenceDataContract(programRepository.findByUuid(s))).collect(Collectors.toList()));
            conceptScopeContract.setEncounterTypes(conceptScope.getEncounterTypes().stream().map(s -> new ReferenceDataContract(encounterTypeRepository.findByUuid(s))).collect(Collectors.toList()));
        }
        return dashboardFilterContract;
    }
}

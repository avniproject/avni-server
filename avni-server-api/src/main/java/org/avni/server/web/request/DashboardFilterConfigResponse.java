package org.avni.server.web.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;

import java.util.List;
import java.util.stream.Collectors;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DashboardFilterConfigResponse {
    private ReferenceDataContract subjectType;
    private String type;
    private String typeTitle;
    private String widget;
    private GroupSubjectTypeScopeResponse groupSubjectTypeScope;
    private ConceptScopeResponse conceptScope;

    public ReferenceDataContract getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(ReferenceDataContract subjectType) {
        this.subjectType = subjectType;
    }

    public String getType() {
        return type;
    }

    public void setType(DashboardFilter.FilterType type) {
        this.type = type.name();
        this.typeTitle = type.getTitle();
    }

    public String getWidget() {
        return widget;
    }

    public void setWidget(String widget) {
        this.widget = widget;
    }

    public GroupSubjectTypeScopeResponse getGroupSubjectTypeScope() {
        return groupSubjectTypeScope;
    }

    public void setGroupSubjectTypeScope(GroupSubjectTypeScopeResponse groupSubjectTypeScope) {
        this.groupSubjectTypeScope = groupSubjectTypeScope;
    }

    public ConceptScopeResponse getConceptScope() {
        return conceptScope;
    }

    public void setConceptScope(ConceptScopeResponse conceptScope) {
        this.conceptScope = conceptScope;
    }

    public void setTypeTitle(String typeTitle) {
        this.typeTitle = typeTitle;
    }

    public String getTypeTitle() {
        return typeTitle;
    }

    public JsonObject toJsonObject() {
        JsonObject jsonObject = new JsonObject();
        jsonObject.with(DashboardFilter.DashboardFilterConfig.TypeFieldName, this.type)
                .with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, this.subjectType.getUuid())
                .with(DashboardFilter.DashboardFilterConfig.WidgetFieldName, this.widget);
        if (this.type.equals(DashboardFilter.FilterType.GroupSubject.name()))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.ScopeFieldName, groupSubjectTypeScope.getJsonObject());
        else if (this.type.equals(DashboardFilter.FilterType.Concept.name()))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.ScopeFieldName, conceptScope.getJsonObject());
        return jsonObject;
    }

    public static class GroupSubjectTypeScopeResponse {
        private ReferenceDataContract subjectType;

        public ReferenceDataContract getSubjectType() {
            return subjectType;
        }

        public void setSubjectType(ReferenceDataContract subjectType) {
            this.subjectType = subjectType;
        }

        public JsonObject getJsonObject() {
            return new JsonObject().with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, subjectType.getUuid());
        }
    }

    public static class ConceptScopeResponse {
        private ConceptContract concept;
        private List<ReferenceDataContract> programs;
        private List<ReferenceDataContract> encounterTypes;

        public List<ReferenceDataContract> getPrograms() {
            return programs;
        }

        public void setPrograms(List<ReferenceDataContract> programs) {
            this.programs = programs;
        }

        public List<ReferenceDataContract> getEncounterTypes() {
            return encounterTypes;
        }

        public void setEncounterTypes(List<ReferenceDataContract> encounterTypes) {
            this.encounterTypes = encounterTypes;
        }

        public void setConcept(ConceptContract concept) {
            this.concept = concept;
        }

        public JsonObject getJsonObject() {
            JsonObject jsonObject = new JsonObject();
            jsonObject.put(DashboardFilter.ConceptScope.ConceptFieldName, concept.getUuid());
            jsonObject.put(DashboardFilter.ConceptScope.ProgramsFieldName, programs.stream().map(ReferenceDataContract::getUuid).collect(Collectors.toList()));
            jsonObject.put(DashboardFilter.ConceptScope.EncounterTypesFieldName, encounterTypes.stream().map(ReferenceDataContract::getUuid).collect(Collectors.toList()));
            return jsonObject;
        }
    }
}

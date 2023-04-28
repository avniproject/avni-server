package org.avni.server.domain.app.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.avni.server.domain.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;

@Entity
@Table(name = "dashboard_filter")
@BatchSize(size = 100)
public class DashboardFilter extends OrganisationAwareEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id")
    private Dashboard dashboard;

    @Type(type = "jsonObject")
    private JsonObject filterConfig;

    @Column
    private String name;

    @JsonIgnore
    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public DashboardFilterConfig getFilterConfig() {
        return new DashboardFilterConfig(filterConfig);
    }

    public void setFilterConfig(JsonObject filterConfig) {
        this.filterConfig = filterConfig;
    }

    public class DashboardFilterConfig {
        public static final String TypeFieldName = "type";
        public static final String SubjectTypeFieldName = "subjectTypeUUID";
        public static final String ScopeFieldName = "scope";
        public static final String WidgetFieldName = "widget";

        private final JsonObject jsonObject;

        public DashboardFilterConfig(JsonObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        public String getSubjectTypeUuid() {
            return (String) this.jsonObject.get(SubjectTypeFieldName);
        }

        public String getWidget() {
            return (String) this.jsonObject.get(WidgetFieldName);
        }

        public FilterType getType() {
            return FilterType.valueOf((String) this.jsonObject.get(TypeFieldName));
        }

        public GroupSubjectTypeFilter getGroupSubjectTypeFilter() {
            JsonObject jsonObject = (JsonObject) this.jsonObject.get(ScopeFieldName);
            return GroupSubjectTypeFilter.fromDatabase(jsonObject);
        }

        public ObservationBasedFilter getObservationBasedFilter() {
            JsonObject jsonObject = (JsonObject) this.jsonObject.get(ScopeFieldName);
            return ObservationBasedFilter.fromDatabase(jsonObject);
        }
    }

    public static class GroupSubjectTypeFilter {
        private JsonObject jsonObject;

        public static GroupSubjectTypeFilter fromDatabase(JsonObject jsonObject) {
            GroupSubjectTypeFilter groupSubjectTypeFilter = new GroupSubjectTypeFilter();
            groupSubjectTypeFilter.jsonObject = jsonObject;
            return groupSubjectTypeFilter;
        }

        public void setSubjectTypeUUID(String uuid) {
            jsonObject.put(DashboardFilterConfig.SubjectTypeFieldName, uuid);
        }

        public String getSubjectTypeUUID() {
            return (String) jsonObject.get(DashboardFilterConfig.SubjectTypeFieldName);
        }
    }

    public static class ObservationBasedFilter {
        public static final String ConceptFieldName = "conceptUUID";
        public static final String ProgramsFieldName = "programUUIDs";
        public static final String EncounterTypesFieldName = "encounterTypeUUIDs";
        private JsonObject jsonObject;

        public static ObservationBasedFilter fromDatabase(JsonObject jsonObject) {
            ObservationBasedFilter conceptScope = new ObservationBasedFilter();
            conceptScope.jsonObject = jsonObject;
            return conceptScope;
        }

        public String getConcept() {
            return (String) jsonObject.get(ConceptFieldName);
        }

        public void setConcept(String uuid) {
            jsonObject.put(ConceptFieldName, uuid);
        }

        public List<String> getPrograms() {
            return (List<String>) jsonObject.get(ProgramsFieldName);
        }

        public void setPrograms(List<String> uuids) {
            jsonObject.put(ProgramsFieldName, uuids);
        }

        public List<String> getEncounterTypes() {
            return (List<String>) jsonObject.get(EncounterTypesFieldName);
        }

        public void setEncounterTypes(List<String> uuids) {
            jsonObject.put(EncounterTypesFieldName, uuids);
        }
    }

    public static enum FilterType {
        RegistrationDate,
        Gender,
        EnrolmentDate,
        ProgramEncounterDate,
        EncounterDate,
        GroupSubject,
        Address,
        Concept;
    }
}

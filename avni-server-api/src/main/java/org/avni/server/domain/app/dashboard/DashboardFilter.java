package org.avni.server.domain.app.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

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

    @JsonIgnore
    public DashboardFilterConfig getFilterConfig() {
        return new DashboardFilterConfig(filterConfig);
    }

    @JsonProperty("filterConfig")
    public JsonObject getFilterConfigJsonString() {
        return filterConfig;
    }

    public void setFilterConfig(JsonObject filterConfig) {
        this.filterConfig = filterConfig;
    }

    public class DashboardFilterConfig {
        public static final String TypeFieldName = "type";
        public static final String SubjectTypeFieldName = "subjectTypeUUID";
        public static final String WidgetFieldName = "widget";
        public static final String ObservationBasedFilterName = "observationBasedFilter";
        public static final String GroupSubjectTypeFilterName = "groupSubjectTypeFilter";

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
            JsonObject jsonObject = new JsonObject((Map<String, Object>) this.jsonObject.get(DashboardFilterConfig.GroupSubjectTypeFilterName));
            return GroupSubjectTypeFilter.fromDatabase(jsonObject);
        }

        public ObservationBasedFilter getObservationBasedFilter() {
            JsonObject jsonObject = new JsonObject((Map<String, Object>) this.jsonObject.get(ObservationBasedFilterName));
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
        public static final String ScopeFieldName = "scope";
        public static final String ConceptFieldName = "conceptUUID";
        public static final String ProgramsFieldName = "programUUIDs";
        public static final String EncounterTypesFieldName = "encounterTypeUUIDs";
        private JsonObject jsonObject;

        public static ObservationBasedFilter fromDatabase(JsonObject jsonObject) {
            ObservationBasedFilter observationBasedFilter = new ObservationBasedFilter();
            observationBasedFilter.jsonObject = jsonObject;
            return observationBasedFilter;
        }

        public String getScope() {
            return (String) jsonObject.get(ScopeFieldName);
        }

        public String getConcept() {
            return (String) jsonObject.get(ConceptFieldName);
        }

        public List<String> getPrograms() {
            return (List<String>) jsonObject.get(ProgramsFieldName);
        }

        public List<String> getEncounterTypes() {
            return (List<String>) jsonObject.get(EncounterTypesFieldName);
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
        Concept,
        SubjectType;
    }
}

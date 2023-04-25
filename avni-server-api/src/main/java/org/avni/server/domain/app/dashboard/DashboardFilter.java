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
        public static final String SubjectTypeFieldName = "subjectType";
        public static final String ScopeFieldName = "scope";
        public static final String WidgetFieldName = "widget";

        private final JsonObject jsonObject;

        public DashboardFilterConfig(JsonObject jsonObject) {
            this.jsonObject = jsonObject;
        }

        public FilterType getConfigType() {
            return FilterType.valueOf((String) this.jsonObject.get(TypeFieldName));
        }

        public String getConfigSubjectTypeUuid() {
            return (String) this.jsonObject.get(SubjectTypeFieldName);
        }

        public String getWidget() {
            return (String) this.jsonObject.get(WidgetFieldName);
        }

        public GroupSubjectTypeScope getGroupSubjectTypeScope() {
            JsonObject jsonObject = (JsonObject) this.jsonObject.get(ScopeFieldName);
            return GroupSubjectTypeScope.fromDatabase(jsonObject);
        }

        public ConceptScope getConceptScope() {
            JsonObject jsonObject = (JsonObject) this.jsonObject.get(ScopeFieldName);
            return ConceptScope.fromDatabase(jsonObject);
        }
    }

    public static class GroupSubjectTypeScope {
        private JsonObject jsonObject;

        public static GroupSubjectTypeScope fromDatabase(JsonObject jsonObject) {
            GroupSubjectTypeScope groupSubjectTypeScope = new GroupSubjectTypeScope();
            groupSubjectTypeScope.jsonObject = jsonObject;
            return groupSubjectTypeScope;
        }

        public void setSubjectType(String uuid) {
            jsonObject.put(DashboardFilterConfig.SubjectTypeFieldName, uuid);
        }

        public String getSubjectType() {
            return (String) jsonObject.get(DashboardFilterConfig.SubjectTypeFieldName);
        }
    }

    public static class ConceptScope {
        public static final String ConceptFieldName = "concept";
        public static final String ProgramsFieldName = "programs";
        public static final String EncounterTypesFieldName = "encounterTypes";
        private JsonObject jsonObject;

        public static ConceptScope fromDatabase(JsonObject jsonObject) {
            ConceptScope conceptScope = new ConceptScope();
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
        RegistrationDate("Registration Date"),
        Gender,
        EnrolmentDate("Enrolment Date"),
        ProgramEncounterDate("Program Encounter Date"),
        EncounterDate("Encounter Date"),
        GroupSubject("Group Subject"),
        Address,
        Concept;

        private final String title;

        FilterType(String title) {
            this.title = title;
        }

        FilterType() {
            this.title = this.name();
        }

        public String getTitle() {
            return title;
        }
    }
}

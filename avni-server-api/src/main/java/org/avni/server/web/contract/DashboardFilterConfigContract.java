package org.avni.server.web.contract;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.web.contract.reports.ObservationBasedFilterContract;

public abstract class DashboardFilterConfigContract {
    private String type;
    private String subjectTypeUUID;
    private String widget;
    private GroupSubjectTypeFilterContract groupSubjectTypeFilter;
    private String presetSubjectTypeUUID;
    private String presetProgramUUID;

    public String getSubjectTypeUUID() {
        return subjectTypeUUID;
    }

    public void setSubjectTypeUUID(String subjectTypeUUID) {
        this.subjectTypeUUID = subjectTypeUUID;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPresetSubjectTypeUUID() {
        return presetSubjectTypeUUID;
    }

    public void setPresetSubjectTypeUUID(String presetSubjectTypeUUID) {
        this.presetSubjectTypeUUID = presetSubjectTypeUUID;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String getPresetProgramUUID() {
        return presetProgramUUID;
    }

    public void setPresetProgramUUID(String presetProgramUUID) {
        this.presetProgramUUID = presetProgramUUID;
    }

    public String getWidget() {
        return widget;
    }

    public void setWidget(String widget) {
        this.widget = widget;
    }

    public GroupSubjectTypeFilterContract getGroupSubjectTypeFilter() {
        return groupSubjectTypeFilter;
    }

    public void setGroupSubjectTypeFilter(GroupSubjectTypeFilterContract groupSubjectTypeScope) {
        this.groupSubjectTypeFilter = groupSubjectTypeScope;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public static class GroupSubjectTypeFilterContract {
        private String subjectTypeUUID;

        public String getSubjectTypeUUID() {
            return subjectTypeUUID;
        }

        public void setSubjectTypeUUID(String subjectTypeUUID) {
            this.subjectTypeUUID = subjectTypeUUID;
        }

        @JsonIgnore
        public JsonObject getJsonObject() {
            return new JsonObject().with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, subjectTypeUUID);
        }
    }

    public abstract ObservationBasedFilterContract newObservationBasedFilter();

    protected JsonObject toJsonObject(JsonObject observationBasedFilter) {
        JsonObject jsonObject = new JsonObject();
        DashboardFilter.FilterType filterType = DashboardFilter.FilterType.valueOf(this.getType());
        jsonObject.with(DashboardFilter.DashboardFilterConfig.TypeFieldName, this.getType())
                .with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, this.getSubjectTypeUUID())
                .with(DashboardFilter.DashboardFilterConfig.WidgetFieldName, this.getWidget());
        if (filterType.equals(DashboardFilter.FilterType.GroupSubject))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.GroupSubjectTypeFilterName, getGroupSubjectTypeFilter().getJsonObject());
        else if (filterType.equals(DashboardFilter.FilterType.Concept))
            jsonObject.put(DashboardFilter.DashboardFilterConfig.ObservationBasedFilterName, observationBasedFilter);
        else if (filterType.equals(DashboardFilter.FilterType.SubjectType))
            jsonObject.withEmptyCheckAndTrim(DashboardFilter.DashboardFilterConfig.PresetSubjectTypeFieldName, this.getPresetSubjectTypeUUID())
                    .withEmptyCheckAndTrim(DashboardFilter.DashboardFilterConfig.PresetProgramFieldName, this.getPresetProgramUUID());
        return jsonObject;
    }
}

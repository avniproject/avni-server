package org.avni.server.web.contract;

import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;

public class DashboardFilterConfigContract {
    private String type;
    private String subjectTypeUUID;
    private String widget;
    private GroupSubjectTypeFilterContract groupSubjectTypeResponse;

    public String getSubjectTypeUUID() {
        return subjectTypeUUID;
    }

    public void setSubjectTypeUUID(String subjectTypeUUID) {
        this.subjectTypeUUID = subjectTypeUUID;
    }

    public String getWidget() {
        return widget;
    }

    public void setWidget(String widget) {
        this.widget = widget;
    }

    public GroupSubjectTypeFilterContract getGroupSubjectTypeScope() {
        return groupSubjectTypeResponse;
    }

    public void setGroupSubjectTypeScope(GroupSubjectTypeFilterContract groupSubjectTypeScope) {
        this.groupSubjectTypeResponse = groupSubjectTypeScope;
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

        public JsonObject getJsonObject() {
            return new JsonObject().with(DashboardFilter.DashboardFilterConfig.SubjectTypeFieldName, subjectTypeUUID);
        }
    }
}

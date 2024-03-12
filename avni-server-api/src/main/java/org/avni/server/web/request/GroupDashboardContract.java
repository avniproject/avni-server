package org.avni.server.web.request;

import org.avni.server.domain.GroupDashboard;

public class GroupDashboardContract extends CHSRequest {
    private boolean isPrimaryDashboard;
    private boolean isSecondaryDashboard;
    private long dashboardId;
    private long groupId;
    private String dashboardName;
    private String dashboardDescription;

    public boolean isPrimaryDashboard() {
        return isPrimaryDashboard;
    }

    public boolean isSecondaryDashboard() {
        return isSecondaryDashboard;
    }

    public long getDashboardId() {
        return dashboardId;
    }

    public long getGroupId() {
        return groupId;
    }

    public String getDashboardName() {
        return dashboardName;
    }

    public String getDashboardDescription() {
        return dashboardDescription;
    }

    public static GroupDashboardContract fromEntity(GroupDashboard groupDashboard) {
        GroupDashboardContract groupDashboardContract = new GroupDashboardContract();
        groupDashboardContract.setId(groupDashboard.getId());
        groupDashboardContract.setUuid(groupDashboard.getUuid());
        groupDashboardContract.setVoided(groupDashboard.isVoided());
        groupDashboardContract.isPrimaryDashboard = groupDashboard.isPrimaryDashboard();
        groupDashboardContract.isSecondaryDashboard = groupDashboard.isSecondaryDashboard();
        groupDashboardContract.groupId = groupDashboard.getGroup().getId();
        groupDashboardContract.dashboardId = groupDashboard.getDashboard().getId();
        groupDashboardContract.dashboardName = groupDashboard.getDashboard().getName();
        groupDashboardContract.dashboardDescription = groupDashboard.getDashboard().getDescription();
        return groupDashboardContract;
    }
}

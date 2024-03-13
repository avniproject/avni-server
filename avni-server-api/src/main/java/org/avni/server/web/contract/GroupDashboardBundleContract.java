package org.avni.server.web.contract;

import org.avni.server.domain.GroupDashboard;

public class GroupDashboardBundleContract extends BaseBundleContract {
    private boolean isPrimaryDashboard;
    private boolean isSecondaryDashboard;
    private String dashboardUUID;
    private String groupUUID;
    private String dashboardName;
    private String dashboardDescription;

    public static GroupDashboardBundleContract fromEntity(GroupDashboard groupDashboard) {
        GroupDashboardBundleContract contract = new GroupDashboardBundleContract();
        contract.setUuid(groupDashboard.getUuid());
        contract.setVoided(groupDashboard.isVoided());
        contract.isPrimaryDashboard = groupDashboard.isPrimaryDashboard();
        contract.isSecondaryDashboard = groupDashboard.isSecondaryDashboard();
        contract.groupUUID = groupDashboard.getGroup().getUuid();
        contract.dashboardUUID = groupDashboard.getDashboard().getUuid();
        contract.dashboardName = groupDashboard.getDashboard().getName();
        contract.dashboardDescription = groupDashboard.getDashboard().getDescription();
        return contract;
    }

    public boolean isPrimaryDashboard() {
        return isPrimaryDashboard;
    }

    public void setPrimaryDashboard(boolean primaryDashboard) {
        isPrimaryDashboard = primaryDashboard;
    }

    public boolean isSecondaryDashboard() {
        return isSecondaryDashboard;
    }

    public void setSecondaryDashboard(boolean secondaryDashboard) {
        isSecondaryDashboard = secondaryDashboard;
    }

    public String getDashboardUUID() {
        return dashboardUUID;
    }

    public void setDashboardUUID(String dashboardUUID) {
        this.dashboardUUID = dashboardUUID;
    }

    public String getGroupUUID() {
        return groupUUID;
    }

    public void setGroupUUID(String groupUUID) {
        this.groupUUID = groupUUID;
    }

    public String getDashboardName() {
        return dashboardName;
    }

    public void setDashboardName(String dashboardName) {
        this.dashboardName = dashboardName;
    }

    public String getDashboardDescription() {
        return dashboardDescription;
    }

    public void setDashboardDescription(String dashboardDescription) {
        this.dashboardDescription = dashboardDescription;
    }
}

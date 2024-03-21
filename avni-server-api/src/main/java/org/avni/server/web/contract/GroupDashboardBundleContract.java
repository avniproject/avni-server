package org.avni.server.web.contract;

import org.avni.server.domain.GroupDashboard;

public class GroupDashboardBundleContract extends BaseBundleContract {
    private boolean isPrimaryDashboard;
    private boolean isSecondaryDashboard;
    private String dashboardUUID;
    private String groupUUID;
    private String groupName;
    private boolean isGroupOneOfTheDefaultGroups;
    private String dashboardName;
    private String dashboardDescription;

    public static GroupDashboardBundleContract fromEntity(GroupDashboard groupDashboard) {
        GroupDashboardBundleContract contract = new GroupDashboardBundleContract();
        contract.setUuid(groupDashboard.getUuid());
        contract.setVoided(groupDashboard.isVoided());
        contract.isPrimaryDashboard = groupDashboard.isPrimaryDashboard();
        contract.isSecondaryDashboard = groupDashboard.isSecondaryDashboard();
        contract.groupUUID = groupDashboard.getGroup().getUuid();
        contract.groupName = groupDashboard.getGroup().getName();
        contract.isGroupOneOfTheDefaultGroups = groupDashboard.getGroup().isOneOfTheDefaultGroups();
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

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public boolean isGroupOneOfTheDefaultGroups() {
        return isGroupOneOfTheDefaultGroups;
    }

    public void setGroupIsOneOfTheDefaultGroups(boolean oneOfTheDefaultGroups) {
        isGroupOneOfTheDefaultGroups = oneOfTheDefaultGroups;
    }
}

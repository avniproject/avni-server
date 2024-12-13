package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;

@Entity
@BatchSize(size = 100)
public class GroupDashboard extends  OrganisationAwareEntity {
    @Column
    private boolean isPrimaryDashboard;

    @Column
    private boolean isSecondaryDashboard;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "group_id")
    private Group group;

    @NotNull
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "dashboard_id")
    private Dashboard dashboard;

    public boolean isPrimaryDashboard() {
        return isPrimaryDashboard;
    }

    public void setPrimaryDashboard(boolean isPrimaryDashboard) {
        this.isPrimaryDashboard = isPrimaryDashboard;
    }

    public void setPrimaryDashboard(Boolean isPrimaryDashboard) {
        this.isPrimaryDashboard = isPrimaryDashboard;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public boolean isSecondaryDashboard() {
        return isSecondaryDashboard;
    }

    public void setSecondaryDashboard(boolean secondaryDashboard) {
        isSecondaryDashboard = secondaryDashboard;
    }
}

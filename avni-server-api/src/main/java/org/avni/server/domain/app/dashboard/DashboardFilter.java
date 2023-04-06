package org.avni.server.domain.app.dashboard;

import org.avni.server.domain.Dashboard;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.OrganisationAwareEntity;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "dashboard_filter")
@BatchSize(size = 100)
public class DashboardFilter extends OrganisationAwareEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id")
    private Dashboard dashboard;

    @Column
    @Type(type = "observations")
    private ObservationCollection filter;

    private String name;

    public Dashboard getDashboard() {
        return dashboard;
    }

    public void setDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public ObservationCollection getFilter() {
        return filter;
    }

    public void setFilter(ObservationCollection filter) {
        this.filter = filter;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

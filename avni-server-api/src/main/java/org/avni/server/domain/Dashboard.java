package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.hibernate.annotations.BatchSize;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "dashboard")
@BatchSize(size = 100)
@JsonIgnoreProperties({"dashboardSections"})
public class Dashboard extends OrganisationAwareEntity {

    @NotNull
    @Column
    private String name;

    @Column
    private String description;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "dashboard")
    private Set<DashboardSection> dashboardSections = new HashSet<>();

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "dashboard")
    private Set<DashboardFilter> dashboardFilters = new HashSet<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Set<DashboardSection> getDashboardSections() {
        return dashboardSections;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setDashboardSections(Set<DashboardSection> dashboardSections) {
        this.dashboardSections.clear();
        this.dashboardSections.addAll(dashboardSections);
    }

    public Set<DashboardFilter> getDashboardFilters() {
        return dashboardFilters;
    }

    public void addUpdateFilter(DashboardFilter dashboardFilter) {
        dashboardFilters.add(dashboardFilter);
        dashboardFilter.setDashboard(this);
    }

    public DashboardSection getSection(String sectionUUID) {
        return dashboardSections.stream().filter(section -> section.getUuid().equals(sectionUUID)).findFirst().orElse(null);
    }

    public void addSection(DashboardSection section) {
        dashboardSections.add(section);
        section.setDashboard(this);
    }
}

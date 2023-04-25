package org.avni.server.web.request;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.service.MetaDataRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DashboardContract extends CHSRequest {
    private String name;
    private String description;
    private List<DashboardSectionContract> sections = new ArrayList<>();
    private List<DashboardFilterContract> filters = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<DashboardSectionContract> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionContract> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterContract> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterContract> filters) {
        this.filters = filters;
    }
}

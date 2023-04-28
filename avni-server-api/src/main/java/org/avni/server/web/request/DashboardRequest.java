package org.avni.server.web.request;

import java.util.ArrayList;
import java.util.List;

public class DashboardRequest extends CHSRequest {
    private String name;
    private String description;
    private List<DashboardSectionContract> sections = new ArrayList<>();
    private List<DashboardFilterRequest> filters = new ArrayList<>();

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

    public List<DashboardFilterRequest> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterRequest> filters) {
        this.filters = filters;
    }
}

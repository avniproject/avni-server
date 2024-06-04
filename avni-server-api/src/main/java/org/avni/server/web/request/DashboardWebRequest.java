package org.avni.server.web.request;

import org.avni.server.web.contract.reports.DashboardContract;
import org.avni.server.web.request.reports.DashboardSectionWebRequest;

import java.util.ArrayList;
import java.util.List;

public class DashboardWebRequest extends DashboardContract {
    private List<DashboardSectionWebRequest> sections = new ArrayList<>();
    private List<DashboardFilterRequest> filters = new ArrayList<>();

    public List<DashboardSectionWebRequest> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionWebRequest> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterRequest> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterRequest> filters) {
        this.filters = filters;
    }
}

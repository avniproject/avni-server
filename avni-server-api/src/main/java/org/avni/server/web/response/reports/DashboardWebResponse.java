package org.avni.server.web.response.reports;

import org.avni.server.web.contract.reports.DashboardContract;
import org.avni.server.web.request.DashboardFilterResponse;

import java.util.ArrayList;
import java.util.List;

public class DashboardWebResponse extends DashboardContract {
    private List<DashboardSectionWebResponse> sections = new ArrayList<>();
    private List<DashboardFilterResponse> filters = new ArrayList<>();

    public List<DashboardSectionWebResponse> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionWebResponse> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterResponse> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterResponse> filters) {
        this.filters = filters;
    }
}

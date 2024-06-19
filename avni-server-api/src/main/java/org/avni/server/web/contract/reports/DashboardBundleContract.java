package org.avni.server.web.contract.reports;

import org.avni.server.web.request.DashboardFilterResponse;

import java.util.ArrayList;
import java.util.List;

public class DashboardBundleContract extends DashboardContract {
    private List<DashboardSectionBundleContract> sections = new ArrayList<>();
    private List<DashboardFilterResponse> filters = new ArrayList<>();

    public List<DashboardSectionBundleContract> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionBundleContract> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterResponse> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterResponse> filters) {
        this.filters = filters;
    }
}

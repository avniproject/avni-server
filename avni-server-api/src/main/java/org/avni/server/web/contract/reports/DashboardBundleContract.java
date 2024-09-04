package org.avni.server.web.contract.reports;

import java.util.ArrayList;
import java.util.List;

public class DashboardBundleContract extends DashboardContract {
    private List<DashboardSectionBundleContract> sections = new ArrayList<>();
    private List<DashboardFilterBundleContract> filters = new ArrayList<>();

    public List<DashboardSectionBundleContract> getSections() {
        return sections;
    }

    public void setSections(List<DashboardSectionBundleContract> sections) {
        this.sections = sections;
    }

    public List<DashboardFilterBundleContract> getFilters() {
        return filters;
    }

    public void setFilters(List<DashboardFilterBundleContract> filters) {
        this.filters = filters;
    }
}

package org.avni.server.web.request;

import org.avni.server.web.external.request.export.ExportFilters;

import java.util.List;

public class ExportFiltersBuilder {
    private ExportFilters exportFilters = new ExportFilters();

    public ExportFiltersBuilder withAddressLevelIds(List<Long> addressLevelIds) {
        exportFilters.setAddressLevelIds(addressLevelIds);
    	return this;
    }

    public ExportFiltersBuilder withDateFilter(ExportFilters.DateFilter dateFilter) {
        exportFilters.setDate(dateFilter);
    	return this;
    }

    public ExportFilters build() {
        return exportFilters;
    }
}

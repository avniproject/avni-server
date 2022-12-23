package org.avni.server.web.contract;

import java.util.List;

public class WebPagedResponse {
    private List data;
    private int page;
    private int totalCount;

    public WebPagedResponse(List data, int page, int totalCount) {
        this.data = data;
        this.page = page;
        this.totalCount = totalCount;
    }

    public List getData() {
        return data;
    }

    public void setData(List data) {
        this.data = data;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}

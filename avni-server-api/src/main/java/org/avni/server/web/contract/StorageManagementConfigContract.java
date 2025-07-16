package org.avni.server.web.contract;

import jakarta.validation.constraints.NotNull;
import org.avni.server.web.request.CHSRequest;

public class StorageManagementConfigContract extends CHSRequest {

    @NotNull
    private String sqlQuery;

    @NotNull
    private String realmQuery;

    private Integer batchSize;

    public String getSqlQuery() {
        return sqlQuery;
    }

    public void setSqlQuery(String sqlQuery) {
        this.sqlQuery = sqlQuery;
    }

    public String getRealmQuery() {
        return realmQuery;
    }

    public void setRealmQuery(String realmQuery) {
        this.realmQuery = realmQuery;
    }

    public Integer getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(Integer batchSize) {
        this.batchSize = batchSize;
    }
}

package org.avni.server.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "storage_management_config")
public class StorageManagementConfig extends OrganisationAwareEntity {

    @Column(name = "sql_query", nullable = false)
    private String sqlQuery;

    @Column(name = "realm_query")
    private String realmQuery;

    @Column(name = "batch_size")
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

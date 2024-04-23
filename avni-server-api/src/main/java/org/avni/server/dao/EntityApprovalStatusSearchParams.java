package org.avni.server.dao;

import org.joda.time.DateTime;


public class EntityApprovalStatusSearchParams {
    private final DateTime lastModifiedDateTime;
    private final DateTime now;
    private final String entityType;
    private final String entityTypeUuid;

    public EntityApprovalStatusSearchParams(DateTime lastModifiedDateTime, DateTime now, String entityType, String entityTypeUuid) {
        this.lastModifiedDateTime = lastModifiedDateTime;
        this.now = now;
        this.entityType = entityType;
        this.entityTypeUuid = entityTypeUuid;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public DateTime getNow() {
        return now;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }
}

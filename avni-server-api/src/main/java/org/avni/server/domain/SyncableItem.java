package org.avni.server.domain;

import org.avni.server.dao.sync.SyncEntityName;

public class SyncableItem {
    private final SyncEntityName syncEntityName;
    private final String entityTypeUuid;

    public SyncableItem(SyncEntityName name, String entityTypeUuid) {
        this.syncEntityName = name;
        this.entityTypeUuid = entityTypeUuid;
    }

    public SyncEntityName getSyncEntityName() {
        return syncEntityName;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }

    @Override
    public String toString() {
        return "{" +
                "name='" + syncEntityName + '\'' +
                ", entityTypeUuid='" + entityTypeUuid + '\'' +
                '}';
    }
}

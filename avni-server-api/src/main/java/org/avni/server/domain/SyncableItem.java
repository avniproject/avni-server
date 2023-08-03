package org.avni.server.domain;

public class SyncableItem {
    private final String name;
    private final String entityTypeUuid;

    public SyncableItem(String name, String entityTypeUuid) {
        this.name = name;
        this.entityTypeUuid = entityTypeUuid;
    }

    public String getName() {
        return name;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }

    @Override
    public String toString() {
        return "{" +
                "name='" + name + '\'' +
                ", entityTypeUuid='" + entityTypeUuid + '\'' +
                '}';
    }
}

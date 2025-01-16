package org.avni.server.web.request;

import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.SyncableItem;
import org.joda.time.DateTime;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

public class EntitySyncStatusContract {
    public static final DateTime REALLY_OLD_DATE = new DateTime("1900-01-01T00:00:00.000Z");
    private String uuid;
    private String entityName;
    private DateTime loadedSince;
    private String entityTypeUuid;

    public static EntitySyncStatusContract createForComparison(String entityName, String entityTypeUuid) {
        EntitySyncStatusContract entitySyncStatusContract = new EntitySyncStatusContract();
        entitySyncStatusContract.entityName = entityName;
        entitySyncStatusContract.entityTypeUuid = entityTypeUuid;
        return entitySyncStatusContract;
    }

    public static EntitySyncStatusContract createForEntityWithSubType(SyncEntityName syncEntityName, String entityTypeUuid) {
        EntitySyncStatusContract contract = new EntitySyncStatusContract();
        contract.setUuid(UUID.randomUUID().toString());
        contract.setLoadedSince(REALLY_OLD_DATE);
        contract.setEntityName(syncEntityName.name());
        contract.setEntityTypeUuid(entityTypeUuid);
        return contract;
    }

    public static EntitySyncStatusContract createForEntityWithoutSubType(SyncEntityName syncEntityName) {
        return EntitySyncStatusContract.createForEntityWithSubType(syncEntityName, null);
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public DateTime getLoadedSince() {
        return loadedSince;
    }

    public void setLoadedSince(DateTime loadedSince) {
        this.loadedSince = loadedSince;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }

    public void setEntityTypeUuid(String entityTypeUuid) {
        this.entityTypeUuid = entityTypeUuid;
    }

    public boolean matchesEntity(SyncableItem syncableItem) {
        return syncableItem.getSyncEntityName().name().equals(this.entityName) && syncableItem.getEntityTypeUuid().equals(this.entityTypeUuid);
    }

    public boolean isApprovalStatusType() {
        return SyncEntityName.approvalStatusEntities.stream().anyMatch(x -> x.nameEquals(entityName));
    }

    public boolean isEncounterOrEnrolmentType() {
        SyncEntityName[] types = {SyncEntityName.Encounter, SyncEntityName.ProgramEncounter, SyncEntityName.ProgramEnrolment};
        return Arrays.stream(types).anyMatch(x -> x.nameEquals(entityName));
    }

    public boolean mightHaveToBeIgnoredDuringSync() {
        return isApprovalStatusType() || isEncounterOrEnrolmentType();
    }

    @Override
    public String toString() {
        return "{" +
                "uuid='" + uuid + '\'' +
                ", entityName='" + entityName + '\'' +
                ", loadedSince=" + loadedSince +
                ", entityTypeUuid='" + entityTypeUuid + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntitySyncStatusContract that = (EntitySyncStatusContract) o;
        return entityName.equals(that.entityName) && Objects.equals(entityTypeUuid, that.entityTypeUuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityName, entityTypeUuid);
    }
}

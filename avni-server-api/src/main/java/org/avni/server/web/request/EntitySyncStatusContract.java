package org.avni.server.web.request;

import org.avni.server.domain.SyncableItem;
import org.joda.time.DateTime;

import java.util.Arrays;
import java.util.UUID;

public class EntitySyncStatusContract {
    private static final DateTime REALLY_OLD_DATE = new DateTime("1900-01-01T00:00:00.000Z");
    private String uuid;
    private String entityName;
    private DateTime loadedSince;
    private String entityTypeUuid;

    public static EntitySyncStatusContract create(String entityName, String entityTypeUuid) {
        EntitySyncStatusContract contract = new EntitySyncStatusContract();
        contract.setUuid(UUID.randomUUID().toString());
        contract.setLoadedSince(REALLY_OLD_DATE);
        contract.setEntityName(entityName);
        contract.setEntityTypeUuid(entityTypeUuid);
        return contract;
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
        return syncableItem.getSyncEntityName().equals(this.entityName) && syncableItem.getEntityTypeUuid().equals(this.entityTypeUuid);
    }

    public boolean isApprovalStatusType() {
        String[] approvalStatuses = {"SubjectEntityApprovalStatus", "EncounterEntityApprovalStatus", "ProgramEncounterEntityApprovalStatus", "ProgramEnrolmentEntityApprovalStatus", "ChecklistItemEntityApprovalStatus"};
        return Arrays.stream(approvalStatuses).anyMatch(this.entityName::equals);
    }

    public boolean isEncounterOrEnrolmentType() {
        String[] types = {"Encounter", "ProgramEncounter", "ProgramEnrolment"};
        return Arrays.stream(types).anyMatch(this.entityName::equals);
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
}

package org.avni.server.dao;

import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.Catchment;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.SubjectType;
import org.joda.time.DateTime;
import org.springframework.data.domain.Pageable;

import java.util.Arrays;
import java.util.List;

public class SyncParameters {
    private final DateTime lastModifiedDateTime;
    private final DateTime now;
    private final Long typeId;
    private String entityTypeUuid;
    private final Pageable pageable;
    private final List<Long> addressLevels;
    private final SubjectType subjectType;
    private final JsonObject syncSettings;
    private final SyncEntityName syncEntityName;
    private final Catchment catchment;

    public SyncParameters(DateTime lastModifiedDateTime,
                          DateTime now, Long typeId,
                          String entityTypeUuid,
                          Pageable pageable,
                          List<Long> addressLevels,
                          SubjectType subjectType,
                          JsonObject syncSettings,
                          SyncEntityName syncEntityName,
                          Catchment catchment) {
        this.lastModifiedDateTime = lastModifiedDateTime;
        this.now = now;
        this.typeId = typeId;
        this.entityTypeUuid = entityTypeUuid;
        this.pageable = pageable;
        this.addressLevels = addressLevels;
        this.subjectType = subjectType;
        this.syncSettings = syncSettings;
        this.syncEntityName = syncEntityName;
        this.catchment = catchment;
    }

    public DateTime getLastModifiedDateTime() {
        return lastModifiedDateTime;
    }

    public DateTime getNow() {
        return now;
    }

    public Long getTypeId() {
        return typeId;
    }

    public Pageable getPageable() {
        return pageable;
    }

    public List<Long> getAddressLevels() {
        return addressLevels;
    }

    public SubjectType getSubjectType() {
        return subjectType;
    }

    public JsonObject getSyncSettings() {
        return syncSettings;
    }

    public boolean isParentOrSelfIndividual() {
        return Arrays.asList(SyncEntityName.Individual, SyncEntityName.Comment, SyncEntityName.CommentThread).contains(syncEntityName);
    }

    public boolean isEncounter() {
        return syncEntityName.equals(SyncEntityName.Encounter);
    }

    public boolean isParentOrSelfEnrolment() {
        return Arrays.asList(SyncEntityName.ProgramEnrolment, SyncEntityName.Checklist, SyncEntityName.ChecklistItem).contains(syncEntityName);
    }

    public boolean isProgramEncounter() {
        return syncEntityName.equals(SyncEntityName.ProgramEncounter);
    }

    public Catchment getCatchment() {
        return catchment;
    }

    public SyncEntityName getSyncEntityName() {
        return syncEntityName;
    }

    public String getEntityTypeUuid() {
        return entityTypeUuid;
    }

    public void setEntityTypeUuid(String entityTypeUuid) {
        this.entityTypeUuid = entityTypeUuid;
    }

    public boolean isModificationCheckOnEntity() {
        return this.getSubjectType() == null || !this.getSubjectType().isDirectlyAssignable()
                || (!this.isParentOrSelfIndividual() && !this.isProgramEncounter() && !this.isEncounter() && !this.isParentOrSelfEnrolment());
    }
}

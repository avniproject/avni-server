package org.avni.server.domain.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

public class ObjectChangeReport {
    private String uuid;
    private ChangeType changeType;
    private Object newValue;
    private Object oldValue;
    @JsonProperty("fieldChanges")
    private final List<FieldChangeReport> fieldChanges = new ArrayList<>();

    public static ObjectChangeReport noChange(String uuid) {
        ObjectChangeReport entityChangeReport = new ObjectChangeReport();
        entityChangeReport.uuid = uuid;
        entityChangeReport.changeType = ChangeType.NoChange;
        return entityChangeReport;
    }

    public static ObjectChangeReport added(String uuid, Object newValue) {
        ObjectChangeReport entityChangeReport = new ObjectChangeReport();
        entityChangeReport.uuid = uuid;
        entityChangeReport.changeType = ChangeType.Added;
        entityChangeReport.newValue = newValue;
        return entityChangeReport;
    }

    public static ObjectChangeReport missing(String uuid, Object oldValue) {
        ObjectChangeReport entityChangeReport = new ObjectChangeReport();
        entityChangeReport.uuid = uuid;
        entityChangeReport.changeType = ChangeType.Missing;
        entityChangeReport.oldValue = oldValue;
        return entityChangeReport;
    }

    public void addFieldReport(FieldChangeReport fieldChangeReport) {
        fieldChanges.add(fieldChangeReport);
        this.changeType = ChangeType.Modified;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public String getUuid() {
        return uuid;
    }

    public Object getNewValue() {
        return newValue;
    }

    public Object getOldValue() {
        return oldValue;
    }
}

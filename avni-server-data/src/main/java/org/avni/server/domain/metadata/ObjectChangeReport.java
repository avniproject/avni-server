package org.avni.server.domain.metadata;

import java.util.ArrayList;
import java.util.List;

public class ObjectChangeReport {
    private String uuid;
    private ChangeType changeType;
    private Object newValue;
    private Object oldValue;
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

    public static ObjectChangeReport removed(String uuid, Object oldValue) {
        ObjectChangeReport entityChangeReport = new ObjectChangeReport();
        entityChangeReport.uuid = uuid;
        entityChangeReport.changeType = ChangeType.Removed;
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
}

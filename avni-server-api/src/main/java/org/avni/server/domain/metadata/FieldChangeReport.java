package org.avni.server.domain.metadata;

public class FieldChangeReport {
    private String fieldName;
    private ChangeType changeType;
    private PrimitiveValueChange primitiveValueChange;
    private ObjectChangeReport objectChangeReport;
    private ObjectCollectionChangeReport collectionChangeReport;

    public static FieldChangeReport added(String fieldName) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Added;
        fieldChangeReport.fieldName = fieldName;
        return fieldChangeReport;
    }

    public static FieldChangeReport removed(String fieldName) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Removed;
        fieldChangeReport.fieldName = fieldName;
        return fieldChangeReport;
    }

    public static FieldChangeReport modified(String fieldName, Object oldValue, Object newValue) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Modified;
        fieldChangeReport.fieldName = fieldName;
        fieldChangeReport.primitiveValueChange = new PrimitiveValueChange(oldValue, newValue);
        return fieldChangeReport;
    }

    public static FieldChangeReport objectModified(String fieldName, ObjectChangeReport objectChangeReport) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Modified;
        fieldChangeReport.fieldName = fieldName;
        fieldChangeReport.objectChangeReport = objectChangeReport;
        return fieldChangeReport;
    }

    public static FieldChangeReport collectionModified(String fieldName, ObjectCollectionChangeReport collectionChangeReport) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Modified;
        fieldChangeReport.fieldName = fieldName;
        fieldChangeReport.collectionChangeReport = collectionChangeReport;
        return fieldChangeReport;
    }

    public String getFieldName() {
        return fieldName;
    }

    public ChangeType getChangeType() {
        return changeType;
    }

    public PrimitiveValueChange getPrimitiveValueChange() {
        return primitiveValueChange;
    }

    public ObjectChangeReport getObjectChangeReport() {
        return objectChangeReport;
    }

    public ObjectCollectionChangeReport getCollectionChangeReport() {
        return collectionChangeReport;
    }
}

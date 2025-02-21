package org.avni.server.domain.metadata;

import org.avni.server.domain.CHSBaseEntity;

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

    public static FieldChangeReport missing(String fieldName) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Missing;
        fieldChangeReport.fieldName = fieldName;
        return fieldChangeReport;
    }

    public static FieldChangeReport modified(String fieldName, Object oldValue, Object newValue) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = getModificationType(fieldName, oldValue, newValue);
        fieldChangeReport.fieldName = fieldName;
        fieldChangeReport.primitiveValueChange = new PrimitiveValueChange(oldValue, newValue);
        return fieldChangeReport;
    }

    public static ChangeType getModificationType(String fieldName, Object oldValue, Object newValue) {
        return fieldName.equals("is_voided") && (oldValue == null || oldValue.equals(Boolean.FALSE)) && newValue.equals(Boolean.TRUE) ? ChangeType.Voided : ChangeType.Modified;
    }
    public static FieldChangeReport objectModified(String fieldName, ObjectChangeReport objectChangeReport) {
        FieldChangeReport fieldChangeReport = new FieldChangeReport();
        fieldChangeReport.changeType = ChangeType.Modified;

        if (objectChangeReport.getOldValue() instanceof CHSBaseEntity oldValue) {
            CHSBaseEntity newValue = (CHSBaseEntity) objectChangeReport.getNewValue();
            if (!oldValue.isVoided() && newValue.isVoided()) {
                fieldChangeReport.changeType = ChangeType.Voided;
            }
        }

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

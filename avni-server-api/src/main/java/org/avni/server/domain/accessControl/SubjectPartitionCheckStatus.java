package org.avni.server.domain.accessControl;

public class SubjectPartitionCheckStatus {
    public static final String NotDirectlyAssignedToThisUser = "notDirectlyAssignedToThisUser";
    public static final String NotInThisUsersCatchment = "notInThisUsersCatchment";
    public static final String SubjectTypeNotConfigured = "subjectTypeNotConfigured";
    public static final String UserSyncAttributeNotConfigured = "userSyncAttributeNotConfigured";
    public static final String SyncAttributeForUserNotValidForUpdate = "syncAttributeForUserNotValidForUpdate";

    private final boolean passed;
    private final String message;

    private SubjectPartitionCheckStatus(boolean passed, String message) {
        this.passed = passed;
        this.message = message;
    }

    public static SubjectPartitionCheckStatus passed() {
        return new SubjectPartitionCheckStatus(true, null);
    }

    public static SubjectPartitionCheckStatus failed(String message) {
        return new SubjectPartitionCheckStatus(false, message);
    }

    public boolean isPassed() {
        return passed;
    }

    public String getMessage() {
        return message;
    }
}

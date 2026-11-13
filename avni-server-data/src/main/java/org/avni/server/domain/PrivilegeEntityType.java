package org.avni.server.domain;

import java.util.Arrays;
import java.util.List;

public enum PrivilegeEntityType {
    Subject,
    Enrolment,
    Encounter,
    Checklist,
    ChecklistItem,
    Task,
    NonTransaction,
    Messaging,
    BulkAccess,
    DataEntryApp,
    // Sharing and receiving health records through ABDM; never passed to an account administrator without a grant.
    Abdm;

    public static final List<PrivilegeEntityType> NotMappedViaForms = Arrays.asList(NonTransaction, BulkAccess, Task, Messaging, DataEntryApp, Abdm);
}

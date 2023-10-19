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
    Analytics,
    TransactionDataOnDataEntryApp;

    public static List<PrivilegeEntityType> NotMappingViaForms = Arrays.asList(NonTransaction, Analytics, Task, Messaging, TransactionDataOnDataEntryApp);
}

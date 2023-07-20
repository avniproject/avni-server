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
    Report;

    public static List<PrivilegeEntityType> NotMappingViaForms = Arrays.asList(NonTransaction, Report, Task);
}

package org.avni.server.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum StandardReportCardTypeType {
    PendingApproval,
    Approved,
    Rejected,
    ScheduledVisits,
    OverdueVisits,
    Total,
    Comments,
    Tasks,
    CallTasks,
    OpenSubjectTasks,
    DueChecklist,
    RecentRegistrations,
    RecentEnrolments,
    RecentVisits;

    private final static List<StandardReportCardTypeType> RecentCardTypes = new ArrayList<StandardReportCardTypeType>() {{
        addAll(Arrays.asList(RecentEnrolments, RecentVisits, RecentRegistrations));
    }};

    private final static List<StandardReportCardTypeType> InputCardTypes = new ArrayList<StandardReportCardTypeType>() {{
        addAll(RecentCardTypes);
        addAll(Arrays.asList(ScheduledVisits, OverdueVisits, Total));
    }};

    public boolean isRecentStandardReportCardType() {
        return RecentCardTypes.contains(this);
    }

    public boolean isInputStandardReportCardType() {
        return InputCardTypes.contains(this);
    }
}

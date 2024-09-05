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

    public boolean isRecentStandardReportCardType() {
        return RecentCardTypes.contains(this);
    }
}

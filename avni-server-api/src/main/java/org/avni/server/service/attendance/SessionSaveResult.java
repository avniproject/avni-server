package org.avni.server.service.attendance;

import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.attendance.Session;

import java.util.List;

public class SessionSaveResult {
    private final Session session;
    private final List<AttendanceRecord> attendanceRecords;
    private final List<FollowUpDescriptor> autoCreatedFollowUps;
    private final List<FollowUpDescriptor> voidedStaleFollowUps;
    private final List<SkippedFollowUp> skippedAlreadyFilledFollowUps;
    private final List<DanglingRefWarning> warnings;

    public SessionSaveResult(Session session,
                             List<AttendanceRecord> attendanceRecords,
                             List<FollowUpDescriptor> autoCreatedFollowUps,
                             List<FollowUpDescriptor> voidedStaleFollowUps,
                             List<SkippedFollowUp> skippedAlreadyFilledFollowUps,
                             List<DanglingRefWarning> warnings) {
        this.session = session;
        this.attendanceRecords = attendanceRecords;
        this.autoCreatedFollowUps = autoCreatedFollowUps;
        this.voidedStaleFollowUps = voidedStaleFollowUps;
        this.skippedAlreadyFilledFollowUps = skippedAlreadyFilledFollowUps;
        this.warnings = warnings;
    }

    public Session getSession() {
        return session;
    }

    public List<AttendanceRecord> getAttendanceRecords() {
        return attendanceRecords;
    }

    public List<FollowUpDescriptor> getAutoCreatedFollowUps() {
        return autoCreatedFollowUps;
    }

    public List<FollowUpDescriptor> getVoidedStaleFollowUps() {
        return voidedStaleFollowUps;
    }

    public List<SkippedFollowUp> getSkippedAlreadyFilledFollowUps() {
        return skippedAlreadyFilledFollowUps;
    }

    public List<DanglingRefWarning> getWarnings() {
        return warnings;
    }
}

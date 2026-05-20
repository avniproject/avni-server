package org.avni.server.web.response.attendance;

import org.avni.server.service.attendance.DanglingRefWarning;
import org.avni.server.service.attendance.FollowUpDescriptor;
import org.avni.server.service.attendance.SessionSaveResult;
import org.avni.server.service.attendance.SkippedFollowUp;
import org.avni.server.web.request.attendance.AttendanceRecordContract;
import org.avni.server.web.request.attendance.SessionContract;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class SessionSaveResponse {
    private final SessionContract session;
    private final List<AttendanceRecordContract> attendanceRecords;
    private final List<FollowUpDescriptor> autoCreatedFollowUps;
    private final List<FollowUpDescriptor> voidedStaleFollowUps;
    private final List<SkippedFollowUp> skippedAlreadyFilledFollowUps;
    private final List<DanglingRefWarning> warnings;

    public SessionSaveResponse(SessionContract session,
                               List<AttendanceRecordContract> attendanceRecords,
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

    public static SessionSaveResponse fromResult(SessionSaveResult result) {
        SessionContract sessionContract = SessionContract.fromEntity(result.getSession(), Collections.emptyList());
        List<AttendanceRecordContract> recordContracts = result.getAttendanceRecords().stream()
                .map(AttendanceRecordContract::fromEntity)
                .collect(Collectors.toList());
        return new SessionSaveResponse(
                sessionContract,
                recordContracts,
                result.getAutoCreatedFollowUps(),
                result.getVoidedStaleFollowUps(),
                result.getSkippedAlreadyFilledFollowUps(),
                result.getWarnings());
    }

    public SessionContract getSession() {
        return session;
    }

    public List<AttendanceRecordContract> getAttendanceRecords() {
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

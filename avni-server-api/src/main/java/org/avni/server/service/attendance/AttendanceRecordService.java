package org.avni.server.service.attendance;

import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.attendance.AttendanceRecord;
import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ScopeAwareService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Service
public class AttendanceRecordService implements ScopeAwareService<AttendanceRecord> {
    private final AttendanceRecordRepository attendanceRecordRepository;

    public AttendanceRecordService(AttendanceRecordRepository attendanceRecordRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID) {
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchment(user, lastModifiedDateTime, SyncEntityName.AttendanceRecord);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<AttendanceRecord> repository() {
        return attendanceRecordRepository;
    }

    public AttendanceRecord save(AttendanceRecord attendanceRecord) {
        attendanceRecord.assignUUIDIfRequired();
        return attendanceRecordRepository.save(attendanceRecord);
    }
}

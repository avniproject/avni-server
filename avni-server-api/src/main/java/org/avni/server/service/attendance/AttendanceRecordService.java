package org.avni.server.service.attendance;

import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.dao.attendance.AttendanceRecordRepository;
import org.avni.server.domain.SubjectType;
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
    private final SubjectTypeRepository subjectTypeRepository;

    public AttendanceRecordService(AttendanceRecordRepository attendanceRecordRepository,
                                   SubjectTypeRepository subjectTypeRepository) {
        this.attendanceRecordRepository = attendanceRecordRepository;
        this.subjectTypeRepository = subjectTypeRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String subjectTypeUuid) {
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUuid);
        if (subjectType == null) return false;
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchmentAndSubjectType(user, lastModifiedDateTime, subjectType.getId(), subjectType, SyncEntityName.AttendanceRecord);
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

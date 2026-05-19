package org.avni.server.service.attendance;

import org.avni.server.dao.attendance.AttendanceTypeRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.attendance.AttendanceType;
import org.avni.server.service.NonScopeAwareService;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

@Service
public class AttendanceTypeService implements NonScopeAwareService {
    private final AttendanceTypeRepository attendanceTypeRepository;

    public AttendanceTypeService(AttendanceTypeRepository attendanceTypeRepository) {
        this.attendanceTypeRepository = attendanceTypeRepository;
    }

    public AttendanceType save(AttendanceType attendanceType) {
        attendanceType.assignUUIDIfRequired();
        return attendanceTypeRepository.save(attendanceType);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return attendanceTypeRepository.existsByLastModifiedDateTimeGreaterThan(CHSEntity.toDate(lastModifiedDateTime));
    }
}

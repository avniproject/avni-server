package org.avni.server.dao.attendance;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.ReferenceDataRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.attendance.AttendanceType;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "attendanceType", path = "attendanceType")
public interface AttendanceTypeRepository extends ReferenceDataRepository<AttendanceType>, FindByLastModifiedDateTime<AttendanceType> {

    List<AttendanceType> findAllByOrganisationId(Long organisationId);

    List<AttendanceType> findBySubjectTypeAndIsVoidedFalse(SubjectType subjectType);

    AttendanceType findBySubjectTypeAndNameIgnoreCaseAndIsVoidedFalse(SubjectType subjectType, String name);
}

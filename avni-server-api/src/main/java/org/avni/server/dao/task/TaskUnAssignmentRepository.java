package org.avni.server.dao.task;

import org.avni.server.dao.FindByLastModifiedDateTime;
import org.avni.server.dao.TransactionalDataRepository;
import org.avni.server.domain.User;
import org.avni.server.domain.task.TaskUnAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface TaskUnAssignmentRepository extends TransactionalDataRepository<TaskUnAssignment>, FindByLastModifiedDateTime<TaskUnAssignment> {

    Page<TaskUnAssignment> findByUnassignedUserAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(User user, Instant lastModifiedDateTime, Instant now, Pageable pageable);

    Slice<TaskUnAssignment> findSliceByUnassignedUserAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(User user, Instant lastModifiedDateTime, Instant now, Pageable pageable);

    boolean existsByUnassignedUserAndLastModifiedDateTimeGreaterThan(User user, Instant lastModifiedDateTime);
}

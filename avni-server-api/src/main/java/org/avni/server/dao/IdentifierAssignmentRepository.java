package org.avni.server.dao;

import org.avni.server.domain.IdentifierAssignment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RepositoryRestResource(collectionResourceRel = "identifierAssignment", path = "identifierAssignment")
public interface IdentifierAssignmentRepository extends TransactionalDataRepository<IdentifierAssignment>, FindByLastModifiedDateTime<IdentifierAssignment> {
    Page<IdentifierAssignment> findByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEqualsOrderByAssignmentOrderAsc(User currentUser, Instant lastModifiedDateTime, String deviceId, Pageable pageable);

    Slice<IdentifierAssignment> findSliceByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEqualsOrderByAssignmentOrderAsc(User currentUser, Instant lastModifiedDateTime, String deviceId, Pageable pageable);

    Integer countIdentifierAssignmentByIdentifierSourceEqualsAndAssignedToEqualsAndIndividualIsNullAndProgramEnrolmentIsNullAndUsedIsFalseAndDeviceIdEquals(IdentifierSource identifierSource, User assignedTo, String deviceId);

    boolean existsByAssignedToAndLastModifiedDateTimeGreaterThanAndIsVoidedFalseAndIndividualIsNullAndProgramEnrolmentIsNullAndDeviceIdEquals(User currentUser, Instant lastModifiedDateTime, String deviceId);
}

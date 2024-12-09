package org.avni.server.dao;

import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.UserSubjectAssignment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Date;
import java.util.List;

@Repository
@RepositoryRestResource(collectionResourceRel = "userSubjectAssignment", path = "userSubjectAssignment", exported = false)
public interface UserSubjectAssignmentRepository extends ReferenceDataRepository<UserSubjectAssignment> {
    UserSubjectAssignment findByUserAndSubjectAndIsVoidedFalse(User user, Individual subject);
    UserSubjectAssignment findByUserAndSubject(User user, Individual subject);
    List<UserSubjectAssignment> findAllBySubjectAndIsVoidedFalse(Individual subject);
    List<UserSubjectAssignment> findAllBySubject(Individual subject);

    List<UserSubjectAssignment> findUserSubjectAssignmentBySubject_IdIn(List<Long> subjectIds);

    boolean existsByUserAndIsVoidedTrueAndLastModifiedDateTimeGreaterThan(User user, Instant lastModifiedDateTime);

    Page<UserSubjectAssignment> findByUserAndIsVoidedTrueAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            User user,
            Instant lastModifiedDate,
            Instant now,
            Pageable pageable
    );

    Slice<UserSubjectAssignment> findSliceByUserAndIsVoidedTrueAndLastModifiedDateTimeIsBetweenOrderByLastModifiedDateTimeAscIdAsc(
            User user,
            Instant lastModifiedDate,
            Instant now,
            Pageable pageable
    );

    default UserSubjectAssignment findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in UserSubjectAssignment.");
    }

    default UserSubjectAssignment findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in UserSubjectAssignment.");
    }

    List<UserSubjectAssignment> findByOrganisationId(Long organisationId);
}

package org.avni.server.dao;

import org.avni.server.domain.AbhaEvent;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.Optional;

// Audit rows are read on the server only; none of this is exposed over REST or sync.
@Repository
@RepositoryRestResource(exported = false)
public interface AbhaEventRepository extends CHSRepository<AbhaEvent>, CustomCHSJpaRepository<AbhaEvent, Long> {
    @Override
    @RestResource(exported = false)
    <S extends AbhaEvent> S save(S entity);

    // Story 14: the patient's current ABHA is the latest record the server received for them. Ordered by the server's
    // receipt time, since phones' clocks differ and the server writes the "removed" records itself.
    @RestResource(exported = false)
    Optional<AbhaEvent> findFirstBySubjectUuidOrderByCreatedDateTimeDescIdDesc(String subjectUuid);

    // Story 14: did this user's record for the subject reach the server lately.
    @RestResource(exported = false)
    boolean existsBySubjectUuidAndUserIdAndCreatedDateTimeAfter(String subjectUuid, Long userId, Date after);
}

package org.avni.server.dao;

import org.avni.server.domain.AbhaEvent;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
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

    // The patient's records, newest first, for the card of a given ABHA (AbhaCardService).
    @RestResource(exported = false)
    List<AbhaEvent> findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(String subjectUuid);

    // Story 14: a patient the server does not have yet. Only the user's own records count, since anyone may post a record
    // naming a UUID the server does not have; the latest of them the server received since the given time.
    @RestResource(exported = false)
    Optional<AbhaEvent> findFirstBySubjectUuidAndUserIdAndCreatedDateTimeAfterOrderByCreatedDateTimeDescIdDesc(String subjectUuid, Long userId, Date after);

    @RestResource(exported = false)
    List<AbhaEvent> findBySubjectUuidAndUserIdAndCreatedDateTimeAfterOrderByCreatedDateTimeDescIdDesc(String subjectUuid, Long userId, Date after);
}

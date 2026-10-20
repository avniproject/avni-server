package org.avni.server.dao;

import org.avni.server.domain.AbhaEvent;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

// Audit rows are read on the server only; none of this is exposed over REST or sync.
@Repository
@RepositoryRestResource(exported = false)
public interface AbhaEventRepository extends CHSRepository<AbhaEvent>, CustomCHSJpaRepository<AbhaEvent, Long> {
    @Override
    @RestResource(exported = false)
    <S extends AbhaEvent> S save(S entity);

    // Story 14: the Eka ID to fetch a card with.
    @RestResource(exported = false)
    Optional<AbhaEvent> findFirstBySubjectUuidAndEkaOidIsNotNullOrderByOccurredAtDesc(String subjectUuid);

    // Story 14: did this user record an ABHA event for the subject lately.
    @RestResource(exported = false)
    boolean existsBySubjectUuidAndUserIdAndOccurredAtAfter(String subjectUuid, Long userId, Instant after);
}

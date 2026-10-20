package org.avni.server.dao;

import org.avni.server.domain.AbhaLink;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.stereotype.Repository;

import java.util.Optional;

// Not exported over Spring Data REST: every row carries a patient's ABHA number.
@Repository
@RepositoryRestResource(exported = false)
public interface AbhaLinkRepository extends TransactionalDataRepository<AbhaLink> {
    Optional<AbhaLink> findByIndividualIdAndIsVoidedFalse(Long individualId);
}

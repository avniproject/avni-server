package org.avni.server.dao;

import org.avni.server.domain.ResetSync;
import org.avni.server.domain.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface ResetSyncRepository extends TransactionalDataRepository<ResetSync> {

    Page<ResetSync> findAllByUserIsNullOrUserAndLastModifiedDateTimeBetweenOrderByLastModifiedDateTimeAscIdAsc(
            User user,
            Instant lastModifiedDateTime,
            Instant now,
            Pageable pageable
    );
}

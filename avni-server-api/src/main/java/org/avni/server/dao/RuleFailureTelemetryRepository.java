package org.avni.server.dao;

import org.avni.server.domain.RuleFailureTelemetry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RuleFailureTelemetryRepository extends AvniJpaRepository<RuleFailureTelemetry, Long> {
    Page<RuleFailureTelemetry> findByIsClosed(Boolean isClosed, Pageable pageable);
    default RuleFailureTelemetry findOne(Long id) {
        return findById(id).orElse(null);
    }

    @Query("select count(r) > 0 from RuleFailureTelemetry r where r.ruleUuid = :ruleUuid and r.individualUuid = :individualUuid and r.errorMessage = :errorMessage and (r.isClosed is null or r.isClosed = false)")
    boolean hasOpenFailure(@Param("ruleUuid") String ruleUuid, @Param("individualUuid") String individualUuid, @Param("errorMessage") String errorMessage);
}

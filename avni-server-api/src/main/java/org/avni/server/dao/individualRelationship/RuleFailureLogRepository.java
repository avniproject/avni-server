package org.avni.server.dao.individualRelationship;

import org.avni.server.dao.AvniCrudRepository;
import org.avni.server.domain.RuleFailureLog;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.rest.core.annotation.RestResource;
import org.springframework.test.context.jdbc.Sql;

public interface RuleFailureLogRepository extends AvniCrudRepository<RuleFailureLog, Long> {

    /**
     * The regular deleteAll tends to delete one by one. This method deletes in batch
     */
    @Override
    @RestResource(exported = false)
    @Modifying
    @Query(value = "delete from rule_failure_log", nativeQuery = true)
    void deleteAll();
}

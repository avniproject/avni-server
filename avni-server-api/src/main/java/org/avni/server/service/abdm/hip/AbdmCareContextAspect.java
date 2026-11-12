package org.avni.server.service.abdm.hip;

import jakarta.persistence.PersistenceException;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.avni.server.domain.AbstractEncounter;
import org.avni.server.service.abdm.AbdmAccess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

// HIP_INTI_LINK_505: queues a saved visit for linking only when the organisation has record sharing on.
@Component
@Aspect
// Outranks @Messageable's hook, so this after-advice runs after it and messaging sees each save unchanged.
@Order(Ordered.LOWEST_PRECEDENCE - 1)
public class AbdmCareContextAspect {
    private static final Logger logger = LoggerFactory.getLogger(AbdmCareContextAspect.class);

    private final AbdmAccess abdmAccess;
    private final AbdmVisitQueue abdmVisitQueue;

    @Autowired
    public AbdmCareContextAspect(AbdmAccess abdmAccess, AbdmVisitQueue abdmVisitQueue) {
        this.abdmAccess = abdmAccess;
        this.abdmVisitQueue = abdmVisitQueue;
    }

    @AfterReturning(value = "@annotation(org.avni.server.common.HealthRecordSaved)", returning = "visit")
    public void queueForLinking(AbstractEncounter visit) {
        if (visit == null) return;
        try {
            if (!abdmAccess.recordSharingOn()) return;
            abdmVisitQueue.queue(visit);
        } catch (DataAccessException | PersistenceException e) {
            // Postgres has aborted the save's transaction; swallowed, the commit would drop the visit without an error.
            throw e;
        } catch (RuntimeException e) {
            logger.warn("ABDM: visit {} not queued for linking: {}", visit.getUuid(), e.getClass().getSimpleName());
        }
    }
}

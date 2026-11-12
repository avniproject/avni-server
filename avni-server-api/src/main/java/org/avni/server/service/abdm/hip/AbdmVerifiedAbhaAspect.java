package org.avni.server.service.abdm.hip;

import jakarta.persistence.PersistenceException;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abha.AbhaEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Visits waiting for their patient's ABHA to be verified are queued again once the phone saves a record of an ABHA with
// an Eka ID for that patient, only for an organisation with record sharing on.
@Component
@Aspect
public class AbdmVerifiedAbhaAspect {
    private static final Logger logger = LoggerFactory.getLogger(AbdmVerifiedAbhaAspect.class);

    private final AbdmAccess abdmAccess;
    private final AbdmVisitQueue abdmVisitQueue;

    @Autowired
    public AbdmVerifiedAbhaAspect(AbdmAccess abdmAccess, AbdmVisitQueue abdmVisitQueue) {
        this.abdmAccess = abdmAccess;
        this.abdmVisitQueue = abdmVisitQueue;
    }

    // stored is false for a record stored before: its first delivery queued them already.
    @AfterReturning(value = "@annotation(org.avni.server.common.AbhaRecordSaved) && args(request)", returning = "stored")
    public void requeueVisitsWaitingForIt(AbhaEventRequest request, Object stored) {
        if (!Boolean.TRUE.equals(stored) || request == null || !StringUtils.hasText(request.getEkaOid())
                || !StringUtils.hasText(request.getSubjectUUID())) return;
        try {
            if (!abdmAccess.recordSharingOn()) return;
            abdmVisitQueue.requeueForVerifiedAbha(request.getSubjectUUID());
        } catch (DataAccessException | PersistenceException e) {
            // Postgres has aborted the save's transaction; swallowed, the commit would drop the record without an error.
            throw e;
        } catch (RuntimeException e) {
            logger.warn("ABDM: visits of subject {} not queued again: {}", request.getSubjectUUID(), e.getClass().getSimpleName());
        }
    }
}

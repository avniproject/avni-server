package org.avni.server.service.abdm.hip;

import jakarta.persistence.PersistenceException;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.avni.server.domain.Individual;
import org.avni.server.service.abdm.AbdmAccess;
import org.avni.server.service.abha.AbhaEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

// Visits waiting for their patient's ABHA to be verified are queued again once the phone saves a record of an ABHA with
// an Eka ID for that patient, and once a saved subject's ABHA is a verified one, only for an organisation with record
// sharing on.
@Component
@Aspect
// After AbhaLinkAspect's after-advice, so a save with no transaction reads the ABHA link it wrote.
@Order(Ordered.LOWEST_PRECEDENCE - 2)
public class AbdmVerifiedAbhaAspect {
    private static final Logger logger = LoggerFactory.getLogger(AbdmVerifiedAbhaAspect.class);

    private final AbdmAccess abdmAccess;
    private final AbdmVisitQueue abdmVisitQueue;

    @Autowired
    public AbdmVerifiedAbhaAspect(AbdmAccess abdmAccess, AbdmVisitQueue abdmVisitQueue) {
        this.abdmAccess = abdmAccess;
        this.abdmVisitQueue = abdmVisitQueue;
    }

    // The corrected form of a patient whose ABHA record the phone sent first: once saved, their visits waiting for a
    // verified ABHA are queued again if the ABHA now on their record is the verified one.
    @AfterReturning(value = "@annotation(org.avni.server.common.SubjectIdentitySaved)", returning = "subject")
    public void requeueIfTheSavedAbhaIsVerified(Individual subject) {
        if (subject == null) return;
        try {
            if (!abdmAccess.recordSharingOn()) return;
            abdmVisitQueue.requeueIfAbhaVerified(subject);
        } catch (DataAccessException | PersistenceException e) {
            // Postgres has aborted the save's transaction; swallowed, the commit would drop the subject without an error.
            throw e;
        } catch (RuntimeException e) {
            logger.warn("ABDM: visits of subject {} not queued again: {}", subject.getUuid(), e.getClass().getSimpleName());
        }
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

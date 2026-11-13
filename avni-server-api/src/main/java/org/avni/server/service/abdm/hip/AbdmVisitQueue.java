package org.avni.server.service.abdm.hip;

import com.bugsnag.Bugsnag;
import jakarta.persistence.PersistenceException;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmSettings;
import org.avni.server.service.abha.AbhaVerifiedRecords;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

// HIP_INTI_LINK_505: "linking of health records should be done automatically once records have been saved at HIP end".
// AbdmCareContextAspect hands each saved visit here, only for an organisation with record sharing on. A completed visit
// of a patient holding an ABHA becomes a QUEUED care context; AbdmHousekeepingJob.linkQueuedVisits links the queue through Eka.
// A visit voided or cancelled has its waiting care context given up, so it is never linked.
// What is needed is read during the save; the row is written after the save commits, in a transaction of its own, so
// a failed write never touches the visit. An organisation without Eka settings, or without a HIP ID, queues nothing.
// Avni starts this link, so the visit goes under the HIP ID of the user who recorded it when that is one of the
// organisation's, else the organisation's first; a visit whose care context exists keeps the HIP ID it was made under.
@Component
public class AbdmVisitQueue {
    private static final Logger logger = LoggerFactory.getLogger(AbdmVisitQueue.class);

    private final AbdmSettings settings;
    private final AbdmHipStore store;
    private final AbhaLinkRepository abhaLinkRepository;
    private final AbhaVerifiedRecords verifiedRecords;
    private final Bugsnag bugsnag;
    private final TransactionTemplate ownTransaction;

    @Autowired
    public AbdmVisitQueue(AbdmSettings settings, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository,
                          PlatformTransactionManager transactionManager, AbhaVerifiedRecords verifiedRecords, Bugsnag bugsnag) {
        this.settings = settings;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.verifiedRecords = verifiedRecords;
        this.bugsnag = bugsnag;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Tests: runs at once, with no transaction of its own.
    AbdmVisitQueue(AbdmSettings settings, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository, AbhaVerifiedRecords verifiedRecords,
                   Bugsnag bugsnag) {
        this.settings = settings;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.verifiedRecords = verifiedRecords;
        this.bugsnag = bugsnag;
        this.ownTransaction = null;
    }

    private record Queued(long individualId, String subjectUuid, List<String> hipIds, String hipId, AbdmHipStore.NewCareContext careContext) {
    }

    public void queue(AbstractEncounter visit) {
        // A visit never done (scheduled, or cancelled or voided before it was) never had a care context: nothing to read or write.
        if (visit == null || visit.getEncounterDateTime() == null) return;
        if (visit.isVoided() || visit.getCancelDateTime() != null) {
            giveUpRemoved(visit);
            return;
        }
        Queued queued;
        try {
            EkaSystemConfig orgSettings = settings.find().orElse(null);
            if (orgSettings == null || orgSettings.getHipIds().isEmpty()) return;
            Individual individual = patientOf(visit);
            if (individual == null || individual.getId() == null) return;
            queued = new Queued(individual.getId(), individual.getUuid(), List.copyOf(orgSettings.getHipIds()),
                    orgSettings.hipIdOr(AbdmHipLinkingService.recorderHipId(visit)),
                    new AbdmHipStore.NewCareContext(visit.getUuid(), AbdmHipLinkingService.display(visit)));
        } catch (DataAccessException | PersistenceException e) {
            // Postgres has aborted the save's transaction; swallowed, the commit would drop the visit without an error.
            throw e;
        } catch (RuntimeException e) {
            logger.warn("ABDM: visit {} not queued for linking: {}", visit.getUuid(), e.getClass().getSimpleName());
            return;
        }
        afterCommitInOwnTransaction("visit " + queued.careContext().reference() + " not queued for linking", () -> write(queued));
    }

    // A visit voided or cancelled: its care context, if one waits, is given up after the save commits, under whichever of
    // the organisation's HIP IDs it was made, so the job never links it.
    private void giveUpRemoved(AbstractEncounter visit) {
        List<String> hipIds;
        try {
            EkaSystemConfig orgSettings = settings.find().orElse(null);
            if (orgSettings == null || orgSettings.getHipIds().isEmpty()) return;
            hipIds = List.copyOf(orgSettings.getHipIds());
        } catch (DataAccessException | PersistenceException e) {
            // Postgres has aborted the save's transaction; swallowed, the commit would drop the visit without an error.
            throw e;
        } catch (RuntimeException e) {
            logger.warn("ABDM: removed visit {} not given up: {}", visit.getUuid(), e.getClass().getSimpleName());
            return;
        }
        String reference = visit.getUuid();
        afterCommitInOwnTransaction("removed visit " + reference + " not given up", () -> store.giveUpRemovedVisits(hipIds, List.of(reference)));
    }

    // An ABHA record with an Eka ID was saved for the patient: if it verifies the ABHA on their saved record, their visits
    // waiting for a verified ABHA are queued again, after the save commits and in a transaction of their own, so a failed
    // write never touches the record.
    public void requeueForVerifiedAbha(String subjectUuid) {
        afterCommitInOwnTransaction("visits of subject " + subjectUuid + " not queued again", () -> {
            boolean verified = abhaLinkRepository.findByIndividualUuidAndIsVoidedFalse(subjectUuid)
                    .flatMap(link -> verifiedRecords.of(subjectUuid, link.getAbhaNumber(), link.getAbhaAddress()))
                    .isPresent();
            if (verified) requeue(subjectUuid);
        });
    }

    // A saved subject whose ABHA now has a record with an Eka ID, as when a corrected ABHA's record came before its form:
    // their visits waiting for a verified ABHA are queued again, after the save commits, reading what it committed.
    public void requeueIfAbhaVerified(Individual subject) {
        if (subject == null || subject.getId() == null || subject.getUuid() == null) return;
        long individualId = subject.getId();
        String subjectUuid = subject.getUuid();
        afterCommitInOwnTransaction("visits of subject " + subjectUuid + " not queued again", () -> {
            boolean verified = abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(individualId)
                    .flatMap(link -> verifiedRecords.of(subjectUuid, link.getAbhaNumber(), link.getAbhaAddress()))
                    .isPresent();
            if (verified) requeue(subjectUuid);
        });
    }

    private void requeue(String subjectUuid) {
        int requeued = store.requeueForVerifiedAbha(subjectUuid);
        if (requeued > 0) logger.info("ABDM: {} visit(s) of subject {} queued again, its ABHA verified", requeued, subjectUuid);
    }

    // After the save commits, in a transaction of its own, so a failed write never touches what was saved. A write that
    // fails leaves a visit unqueued or unsettled that nothing else would mend, so it is reported, not only logged.
    private void afterCommitInOwnTransaction(String failure, Runnable work) {
        afterCommit(() -> {
            try {
                if (ownTransaction == null) work.run();
                else ownTransaction.executeWithoutResult(status -> work.run());
            } catch (RuntimeException e) {
                logger.warn("ABDM: {}: {}", failure, e.getClass().getSimpleName());
                bugsnag.notify(new IllegalStateException("ABDM: " + failure, e));
            }
        });
    }

    private void afterCommit(Runnable work) {
        if (ownTransaction != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    work.run();
                }
            });
        } else {
            work.run();
        }
    }

    private void write(Queued q) {
        if (abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(q.individualId()).isEmpty()) return;
        store.queueCareContexts(q.individualId(), hipIdOf(q), q.subjectUuid(), AbdmHipLinkingService.HI_TYPE, List.of(q.careContext()));
    }

    // The HIP ID the visit's care context was made under, if it has one; else the one chosen at save.
    private String hipIdOf(Queued q) {
        for (String hipId : q.hipIds()) {
            if (!store.findCareContextsByReferences(hipId, List.of(q.careContext().reference())).isEmpty()) return hipId;
        }
        return q.hipId();
    }

    private static Individual patientOf(AbstractEncounter visit) {
        if (visit instanceof Encounter e) return e.getIndividual();
        if (visit instanceof ProgramEncounter pe && pe.getProgramEnrolment() != null) return pe.getProgramEnrolment().getIndividual();
        return null;
    }
}

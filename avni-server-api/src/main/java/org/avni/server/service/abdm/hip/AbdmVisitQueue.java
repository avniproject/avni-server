package org.avni.server.service.abdm.hip;

import jakarta.persistence.PersistenceException;
import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.extenalSystem.EkaSystemConfig;
import org.avni.server.service.abdm.AbdmSettings;
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
    private final TransactionTemplate ownTransaction;

    @Autowired
    public AbdmVisitQueue(AbdmSettings settings, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository,
                          PlatformTransactionManager transactionManager) {
        this.settings = settings;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Tests: runs at once, with no transaction of its own.
    AbdmVisitQueue(AbdmSettings settings, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository) {
        this.settings = settings;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.ownTransaction = null;
    }

    private record Queued(long individualId, String subjectUuid, List<String> hipIds, String hipId, AbdmHipStore.NewCareContext careContext) {
    }

    public void queue(AbstractEncounter visit) {
        if (visit == null) return;
        Queued queued;
        try {
            if (visit.isVoided() || visit.getEncounterDateTime() == null || visit.getCancelDateTime() != null) return;
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
        if (ownTransaction != null && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    write(queued);
                }
            });
        } else {
            write(queued);
        }
    }

    private void write(Queued q) {
        try {
            Runnable work = () -> {
                if (abhaLinkRepository.findByIndividualIdAndIsVoidedFalse(q.individualId()).isEmpty()) return;
                store.queueCareContexts(q.individualId(), hipIdOf(q), q.subjectUuid(), AbdmHipLinkingService.HI_TYPE, List.of(q.careContext()));
            };
            if (ownTransaction == null) work.run();
            else ownTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            logger.warn("ABDM: visit {} not queued for linking: {}", q.careContext().reference(), e.getClass().getSimpleName());
        }
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

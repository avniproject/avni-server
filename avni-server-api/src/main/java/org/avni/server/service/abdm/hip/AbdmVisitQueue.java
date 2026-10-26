package org.avni.server.service.abdm.hip;

import org.avni.server.dao.AbhaLinkRepository;
import org.avni.server.domain.AbstractEncounter;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.service.abdm.AbdmConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.List;

// DEMO-ONLY abdm-hackathon: never merged.
// HIP_INTI_LINK_505: "linking of health records should be done automatically once records have been saved at HIP end".
// EncounterService.save and ProgramEncounterService.save hand each saved visit here. A completed visit of a patient
// holding an ABHA becomes a QUEUED care context; AbdmHousekeepingJob.linkQueuedVisits links the queue through Eka.
// What is needed is read during the save; the row is written after the save commits, in a transaction of its own, so
// nothing here can fail or slow a sync. AbdmAutoLink switches it off, from abdm.auto-link or the admin screen.
@Component
public class AbdmVisitQueue {
    private static final Logger logger = LoggerFactory.getLogger(AbdmVisitQueue.class);

    private final AbdmConfig cfg;
    private final AbdmHipStore store;
    private final AbhaLinkRepository abhaLinkRepository;
    private final AbdmAutoLink autoLink;
    private final TransactionTemplate ownTransaction;

    @Autowired
    public AbdmVisitQueue(AbdmConfig cfg, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository,
                          AbdmAutoLink autoLink, PlatformTransactionManager transactionManager) {
        this.cfg = cfg;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.autoLink = autoLink;
        this.ownTransaction = new TransactionTemplate(transactionManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    // Tests: runs at once, with no transaction of its own.
    AbdmVisitQueue(AbdmConfig cfg, AbdmHipStore store, AbhaLinkRepository abhaLinkRepository, AbdmAutoLink autoLink) {
        this.cfg = cfg;
        this.store = store;
        this.abhaLinkRepository = abhaLinkRepository;
        this.autoLink = autoLink;
        this.ownTransaction = null;
    }

    private record Queued(long individualId, String subjectUuid, String hipId, AbdmHipStore.NewCareContext careContext) {
    }

    public void queue(AbstractEncounter visit) {
        if (!autoLink.isOn() || visit == null) return;
        Queued queued;
        try {
            if (visit.isVoided() || visit.getEncounterDateTime() == null || visit.getCancelDateTime() != null) return;
            if (!StringUtils.hasText(cfg.getHipId())) return;
            Individual individual = patientOf(visit);
            if (individual == null || individual.getId() == null) return;
            queued = new Queued(individual.getId(), individual.getUuid(), cfg.getHipId(),
                    new AbdmHipStore.NewCareContext(visit.getUuid(), AbdmHipLinkingService.display(visit)));
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
                store.queueCareContexts(q.individualId(), q.hipId(), q.subjectUuid(), AbdmHipLinkingService.HI_TYPE, List.of(q.careContext()));
            };
            if (ownTransaction == null) work.run();
            else ownTransaction.executeWithoutResult(status -> work.run());
        } catch (RuntimeException e) {
            logger.warn("ABDM: visit {} not queued for linking: {}", q.careContext().reference(), e.getClass().getSimpleName());
        }
    }

    private static Individual patientOf(AbstractEncounter visit) {
        if (visit instanceof Encounter e) return e.getIndividual();
        if (visit instanceof ProgramEncounter pe && pe.getProgramEnrolment() != null) return pe.getProgramEnrolment().getIndividual();
        return null;
    }
}

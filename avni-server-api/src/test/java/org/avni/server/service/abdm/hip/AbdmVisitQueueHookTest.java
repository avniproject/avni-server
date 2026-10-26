package org.avni.server.service.abdm.hip;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.ProgramEnrolment;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.EncounterService;
import org.avni.server.service.ProgramEncounterService;
import org.junit.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// DEMO-ONLY abdm-hackathon: both ways a visit is saved (sync, upload, API) hand it to the ABDM queue (HIP_INTI_LINK_505).
public class AbdmVisitQueueHookTest {
    private final AbdmVisitQueue queue = mock(AbdmVisitQueue.class);

    @SuppressWarnings("unchecked")
    private ObjectProvider<AbdmVisitQueue> provider() {
        ObjectProvider<AbdmVisitQueue> provider = mock(ObjectProvider.class);
        doAnswer(inv -> {
            ((Consumer<AbdmVisitQueue>) inv.getArgument(0)).accept(queue);
            return null;
        }).when(provider).ifAvailable(any());
        return provider;
    }

    private static Individual patient() {
        Individual individual = new Individual();
        individual.setSubjectType(new SubjectType());
        return individual;
    }

    @Test
    public void aSavedVisitIsHandedToTheQueue() {
        EncounterRepository repository = mock(EncounterRepository.class);
        EncounterService service = new EncounterService(repository, null, null, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "abdmVisitQueue", provider());
        Encounter visit = new Encounter();
        visit.setIndividual(patient());
        when(repository.saveEntity(visit)).thenReturn(visit);

        service.save(visit);

        verify(queue).queue(visit);
    }

    @Test
    public void aSavedProgrammeVisitIsHandedToTheQueue() {
        ProgramEncounterRepository repository = mock(ProgramEncounterRepository.class);
        ProgramEncounterService service = new ProgramEncounterService(repository, null, null, null, null, null, null, null);
        ReflectionTestUtils.setField(service, "abdmVisitQueue", provider());
        ProgramEnrolment enrolment = new ProgramEnrolment();
        enrolment.setIndividual(patient());
        ProgramEncounter visit = new ProgramEncounter();
        visit.setProgramEnrolment(enrolment);
        when(repository.saveEntity(visit)).thenReturn(visit);

        service.save(visit);

        verify(queue).queue(visit);
    }
}

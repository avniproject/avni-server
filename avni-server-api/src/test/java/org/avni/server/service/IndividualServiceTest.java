package org.avni.server.service;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.dao.ProgramEnrolmentRepository;
import org.avni.server.domain.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class IndividualServiceTest {
    @Mock
    private IndividualRepository individualRepository;

    private IndividualService individualService;
    @Mock
    private EncounterRepository encounterRepository;
    @Mock
    private ProgramEncounterRepository programEncounterRepository;
    @Mock
    private ProgramEnrolmentRepository programEnrolmentRepository;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        individualService = new IndividualService(individualRepository,
                null, null, null, null, null, encounterRepository, programEncounterRepository, null, null, null, programEnrolmentRepository);
    }

    @Test
    public void shouldFindById() {
        Individual individual = mock(Individual.class);
        Long individualId = 123L;
        when(individualRepository.findEntity(individualId)).thenReturn(individual);

        Individual actualIndividual = individualService.findById(individualId);

        verify(individualRepository).findEntity(individualId);
        assertThat(actualIndividual).isEqualToComparingFieldByFieldRecursively(individual);
    }

    @Test
    public void shouldFindIndividualByIdWhenMetadataContainsOnlySubjectTypeName() {
        Individual expectedIndividual = new Individual();
        when(individualRepository.findById(1234L)).thenReturn(Optional.of(expectedIndividual));
        Individual actualIndividual = individualService.findByMetadata("SomesubjectTypeName", null, null, 1234);
        assertEquals(expectedIndividual, actualIndividual);
    }

    @Test
    public void shouldFindIndividualFromProgramEnrolmentWhenMetadataContainsSubjectTypeNameAndProgramName() {
        Individual expectedIndividual = new Individual();
        ProgramEnrolment programEnrolment = new ProgramEnrolment();
        programEnrolment.setIndividual(expectedIndividual);

        when(programEnrolmentRepository.findById(1234L)).thenReturn(Optional.of(programEnrolment));
        Individual actualIndividual = individualService.findByMetadata("SomeSubjectTypeName", "SomeProgramName", null, 1234);
        assertEquals(expectedIndividual, actualIndividual);
    }

    @Test
    public void shouldFindIndividualFromProgramEncounterWhenMetadataContainsSubjectTypeNameProgramNameAndEncounterTypeName() {
        Individual expectedIndividual = new Individual();
        ProgramEncounter programEncounter = new ProgramEncounter();
        programEncounter.setIndividual(expectedIndividual);

        when(programEncounterRepository.findById(1234L)).thenReturn(Optional.of(programEncounter));
        Individual actualIndividual = individualService.findByMetadata("SomeSubjectTypeName", "SomeProgramName", "SomeEncounterType", 1234);
        assertEquals(expectedIndividual, actualIndividual);
    }

    @Test
    public void shouldFindIndividualFromEncounterWhenMetadataContainsSubjectTypeAndEncounterTypeName() {
        Individual expectedIndividual = new Individual();
        Encounter encounter = new Encounter();
        encounter.setIndividual(expectedIndividual);

        when(encounterRepository.findById(1234L)).thenReturn(Optional.of(encounter));
        Individual actualIndividual = individualService.findByMetadata("SomeSubjectTypeName", null, "SomePlainEncounterTypeName", 1234);
        assertEquals(expectedIndividual, actualIndividual);
    }
}

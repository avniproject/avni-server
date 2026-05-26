package org.avni.server.service;

import org.avni.server.dao.ProgramEncounterRepository;
import org.avni.server.domain.ProgramEncounter;
import org.avni.server.domain.User;
import org.avni.server.web.request.ProgramEncounterRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ProgramEncounterServiceTest {

    @Mock
    private ProgramEncounterRepository programEncounterRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private ProgramEncounterService programEncounterService;

    @Test
    public void shouldSetAuditFieldsWhenSavingProgramEncounter() {
        // Arrange
        User currentUser = new User();
        currentUser.setId(1L);
        currentUser.setUsername("testUser");

        when(userService.getCurrentUser()).thenReturn(currentUser);
        when(programEncounterRepository.saveEntity(any())).thenAnswer(i -> i.getArguments()[0]);

        ProgramEncounter encounter = new ProgramEncounter();

        // Act
        ProgramEncounter savedEncounter = programEncounterService.save(encounter);

        // Assert
        assertNotNull(savedEncounter.getCreatedBy());
        assertNotNull(savedEncounter.getLastModifiedBy());
        assertNotNull(savedEncounter.getFilledBy());
        assertEquals(currentUser, savedEncounter.getCreatedBy());
        assertEquals(currentUser, savedEncounter.getLastModifiedBy());
        assertEquals(currentUser, savedEncounter.getFilledBy());
    }
}
package org.avni.server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.avni.server.dao.SyncParameters;
import org.avni.server.dao.SyncableRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.User;
import org.avni.server.domain.sync.SyncEntityName;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ScopeBasedSyncServiceTest {
    private EntityManager entityManager;
    private SyncableRepository<CHSEntity> repository;
    private ScopeBasedSyncService<CHSEntity> scopeBasedSyncService;
    private final User user = new User();

    @Before
    public void setup() {
        entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        repository = mock(SyncableRepository.class);
        scopeBasedSyncService = new ScopeBasedSyncService<>(mock(AddressLevelService.class), entityManager, true);
    }

    @Test
    public void shouldDisableBitmapScansForTheSyncTransactionOfOrganisationOwnedTransactionalEntities() {
        scopeBasedSyncService.getSyncResultsByCatchmentAsSlice(repository, user, new DateTime(0), new DateTime(), PageRequest.of(0, 10), SyncEntityName.ProgramEncounter);

        verify(entityManager).createNativeQuery("set local enable_bitmapscan = off");
        verify(repository).getSyncResultsAsSlice(any());
    }

    @Test
    public void shouldLeavePlannerSettingsAloneForOtherEntities() {
        scopeBasedSyncService.getSyncResultsByCatchmentAsSlice(repository, user, new DateTime(0), new DateTime(), PageRequest.of(0, 10), SyncEntityName.AddressLevel);

        verify(entityManager, never()).createNativeQuery(anyString());
        verify(repository).getSyncResultsAsSlice(any());
    }

    @Test
    public void cursorPageShouldReadFromTheStartOfTheCursorInsteadOfSkippingRows() {
        scopeBasedSyncService.getSyncResultsBySubjectTypeRegistrationLocationAsSlice(repository, user, new DateTime(0), new DateTime(), 1L, PageRequest.of(7, 1000), null, SyncEntityName.ProgramEncounter, "last-row-uuid");

        ArgumentCaptor<SyncParameters> syncParameters = ArgumentCaptor.forClass(SyncParameters.class);
        verify(repository).getSyncResultsAsSlice(syncParameters.capture());
        assertThat(syncParameters.getValue().getAfterUuid()).isEqualTo("last-row-uuid");
        assertThat(syncParameters.getValue().getPageable().getOffset()).isZero();
        assertThat(syncParameters.getValue().getPageable().getPageSize()).isEqualTo(1000);
    }

    @Test
    public void shouldNotTouchPlannerSettingsWhenTheGuardIsSwitchedOff() {
        ScopeBasedSyncService<CHSEntity> withoutGuard = new ScopeBasedSyncService<>(mock(AddressLevelService.class), entityManager, false);

        withoutGuard.getSyncResultsByCatchmentAsSlice(repository, user, new DateTime(0), new DateTime(), PageRequest.of(0, 10), SyncEntityName.ProgramEncounter);

        verify(entityManager, never()).createNativeQuery(anyString());
    }
}

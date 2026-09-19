package org.avni.server.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.avni.server.dao.SyncableRepository;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.User;
import org.avni.server.domain.sync.SyncEntityName;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.springframework.data.domain.PageRequest;

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
        scopeBasedSyncService = new ScopeBasedSyncService<>(mock(AddressLevelService.class), entityManager);
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
}

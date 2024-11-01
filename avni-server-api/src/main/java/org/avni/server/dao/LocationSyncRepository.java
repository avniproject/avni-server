package org.avni.server.dao;

import org.avni.server.domain.AddressLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import javax.transaction.Transactional;

@Repository
public class LocationSyncRepository extends RoleSwitchableRepository implements SyncableRepository<AddressLevel> {
    private final LocationRepository locationRepository;

    public LocationSyncRepository(EntityManager entityManager, LocationRepository locationRepository) {
        super(entityManager);
        this.locationRepository = locationRepository;
    }

    @Override
    public boolean isEntityChanged(SyncParameters syncParameters) {
        return locationRepository.isEntityChanged(syncParameters);
    }

    @Override
    public Slice<AddressLevel> getSyncResultsAsSlice(SyncParameters syncParameters) {
        return locationRepository.getSyncResultsAsSlice(syncParameters);
    }

    @Override
    @Transactional
    public Page<AddressLevel> getSyncResults(SyncParameters syncParameters) {
        try {
            setRoleToNone();
            return locationRepository.getSyncResults(syncParameters);
        } finally {
            setRoleBackToUser();
        }
    }
}

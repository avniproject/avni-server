package org.avni.server.dao;

import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ParentLocationMapping;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * LocationSyncRepository uses the postgres @> keyword for sync, which does not index well and creates poor plans.
 * This is a performance enhancement that temporarily switches out the role of the user before running this query.
 *
 */
@Repository
public class LocationMappingSyncRepository extends RoleSwitchableRepository implements SyncableRepository<ParentLocationMapping> {
    private final LocationMappingRepository locationMappingRepository;

    public LocationMappingSyncRepository(EntityManager entityManager, LocationMappingRepository locationMappingRepository) {
        super(entityManager);
        this.locationMappingRepository = locationMappingRepository;
    }

    @Override
    public boolean isEntityChanged(SyncParameters syncParameters) {
        return locationMappingRepository.isEntityChanged(syncParameters);
    }

    @Override
    public Slice<ParentLocationMapping> getSyncResultsAsSlice(SyncParameters syncParameters) {
        return locationMappingRepository.getSyncResultsAsSlice(syncParameters);
    }


    @Override
    @Transactional
    public Page<ParentLocationMapping> getSyncResults(SyncParameters syncParameters) {
        try {
            setRoleToNone();
            return locationMappingRepository.getSyncResults(syncParameters);
        } finally {
            setRoleBackToUser();
        }
    }
}

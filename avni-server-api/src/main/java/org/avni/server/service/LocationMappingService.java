package org.avni.server.service;

import org.avni.server.domain.sync.SyncEntityName;
import org.avni.server.domain.ParentLocationMapping;
import org.joda.time.DateTime;
import org.avni.server.dao.LocationMappingRepository;
import org.avni.server.dao.OperatingIndividualScopeAwareRepository;
import org.avni.server.domain.User;
import org.avni.server.framework.security.UserContextHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LocationMappingService implements ScopeAwareService<ParentLocationMapping> {

    private final LocationMappingRepository locationMappingRepository;

    @Autowired
    public LocationMappingService(LocationMappingRepository locationMappingRepository) {
        this.locationMappingRepository = locationMappingRepository;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID) {
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchment(user, lastModifiedDateTime, SyncEntityName.LocationMapping);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<ParentLocationMapping> repository() {
        return locationMappingRepository;
    }

}

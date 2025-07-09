package org.avni.server.dao;

import org.avni.server.domain.StorageManagementConfig;

public interface StorageManagementConfigRepository extends ReferenceDataRepository<StorageManagementConfig>, FindByLastModifiedDateTime<StorageManagementConfig> {
    default StorageManagementConfig findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in StorageManagementConfig");
    }
    default StorageManagementConfig findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in StorageManagementConfig");
    }
}

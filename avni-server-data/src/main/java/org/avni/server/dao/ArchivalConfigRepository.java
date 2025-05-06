package org.avni.server.dao;

import org.avni.server.domain.ArchivalConfig;

public interface ArchivalConfigRepository extends ReferenceDataRepository<ArchivalConfig>, FindByLastModifiedDateTime<ArchivalConfig> {
    default ArchivalConfig findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in ArchivalConfig");
    }
    default ArchivalConfig findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in ArchivalConfig");
    }
}

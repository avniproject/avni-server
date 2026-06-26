package org.avni.server.dao;

import org.avni.server.domain.ModelKey;
import org.springframework.stereotype.Repository;

@Repository
public interface ModelKeyRepository extends ReferenceDataRepository<ModelKey> {

    default ModelKey findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in ModelKey");
    }

    default ModelKey findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in ModelKey");
    }

    ModelKey findByOrganisationIdAndSha256AndIsVoidedFalse(Long organisationId, String sha256);
}

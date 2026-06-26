package org.avni.server.dao;

import org.avni.server.domain.ModelKey;
import org.springframework.stereotype.Repository;

/**
 * Server-only model key store repository (avniproject/avni-server#1020). Lookup is by org +
 * {@code sha256} (matching the {@code models/<sha256>.bin} object key). RLS keeps org A out of
 * org B's keys.
 */
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

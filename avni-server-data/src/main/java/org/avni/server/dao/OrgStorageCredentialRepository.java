package org.avni.server.dao;

import org.avni.server.domain.OrgStorageCredential;
import org.springframework.stereotype.Repository;

@Repository
public interface OrgStorageCredentialRepository extends ReferenceDataRepository<OrgStorageCredential> {

    default OrgStorageCredential findByName(String name) {
        throw new UnsupportedOperationException("No field 'name' in OrgStorageCredential");
    }

    default OrgStorageCredential findByNameIgnoreCase(String name) {
        throw new UnsupportedOperationException("No field 'name' in OrgStorageCredential");
    }

    OrgStorageCredential findByOrganisationIdAndCredentialRefAndIsVoidedFalse(Long organisationId, String credentialRef);
}

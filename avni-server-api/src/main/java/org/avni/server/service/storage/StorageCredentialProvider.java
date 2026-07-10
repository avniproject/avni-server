package org.avni.server.service.storage;

import org.avni.server.domain.Organisation;

public interface StorageCredentialProvider {

    StorageTargetCredentials getCredentials(Organisation organisation, String credentialRef);

    // Version stamp used by StorageResolver for cache invalidation; 0 when no credential/version is available.
    long credentialVersion(Organisation organisation, String credentialRef);
}

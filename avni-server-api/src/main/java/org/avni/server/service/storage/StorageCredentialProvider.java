package org.avni.server.service.storage;

import org.avni.server.domain.Organisation;

/**
 * Resolves the credentials for a storage target's {@code credentialRef} (avniproject/avni-server#1012, D14).
 * <p>
 * The seam keeps the credential source swappable: the <b>P0 implementation</b>
 * ({@link EncryptedDbStorageCredentialProvider}) reads from an encrypted-per-org DB store. An
 * env/deploy-config or cloud-secret-manager provider can be added later (P1) without touching the
 * resolver. Per D14, the env/deploy-config provider is deliberately NOT shipped in P0.
 */
public interface StorageCredentialProvider {

    /**
     * @return the decrypted credentials for {@code credentialRef} in {@code organisation}'s scope.
     * @throws StorageConfigurationException if no credential exists or it cannot be decrypted
     *                                       (must fail loud - never silently fall back).
     */
    StorageTargetCredentials getCredentials(Organisation organisation, String credentialRef);

    /**
     * A cheap version stamp for {@code credentialRef} (e.g. last-modified epoch millis) used as a
     * cache-invalidation component by the {@link StorageResolver}, so rotating a credential drops the
     * stale cached client without a JVM restart (avniproject/avni-server#1012). Returns {@code 0} when
     * there is no such credential / no version available.
     */
    long credentialVersion(Organisation organisation, String credentialRef);
}

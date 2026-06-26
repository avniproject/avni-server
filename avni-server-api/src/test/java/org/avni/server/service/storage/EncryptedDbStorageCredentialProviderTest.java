package org.avni.server.service.storage;

import org.avni.server.dao.OrgStorageCredentialRepository;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.OrgStorageCredential;
import org.avni.server.service.CryptoService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class EncryptedDbStorageCredentialProviderTest {

    private static final String MASTER_KEY = "xqtzQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";
    private static final String OTHER_MASTER_KEY = "AAAAQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";

    @Mock
    private OrgStorageCredentialRepository repository;

    private final CryptoService cryptoService = new CryptoService();
    private EncryptedDbStorageCredentialProvider provider;
    private Organisation organisation;

    @Before
    public void setUp() {
        initMocks(this);
        provider = new EncryptedDbStorageCredentialProvider(repository, cryptoService, MASTER_KEY);
        organisation = new Organisation("Test Org");
        organisation.setId(7L);
    }

    private OrgStorageCredential storedCredential(String accessKey, String encryptedSecret) {
        OrgStorageCredential cred = new OrgStorageCredential();
        cred.setCredentialRef("org-gcs-creds");
        cred.setAccessKey(accessKey);
        cred.setEncryptedSecretKey(encryptedSecret);
        return cred;
    }

    @Test
    public void encryptOnWriteDecryptOnReadRoundTrips() throws Exception {
        String plaintextSecret = "super-secret-hmac-key";
        String encrypted = provider.encryptSecret(plaintextSecret);

        assertNotEquals("the stored secret must be ciphertext, not plaintext", plaintextSecret, encrypted);

        when(repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(7L, "org-gcs-creds"))
                .thenReturn(storedCredential("ACCESS123", encrypted));

        StorageTargetCredentials creds = provider.getCredentials(organisation, "org-gcs-creds");

        assertEquals("ACCESS123", creds.getAccessKey());
        assertEquals("decrypt must recover the original secret", plaintextSecret, creds.getSecretKey());
    }

    @Test
    public void wrongMasterKeyFailsLoud() throws Exception {
        String encrypted = provider.encryptSecret("super-secret-hmac-key");
        when(repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(7L, "org-gcs-creds"))
                .thenReturn(storedCredential("ACCESS123", encrypted));

        EncryptedDbStorageCredentialProvider wrongKeyProvider =
                new EncryptedDbStorageCredentialProvider(repository, cryptoService, OTHER_MASTER_KEY);

        try {
            wrongKeyProvider.getCredentials(organisation, "org-gcs-creds");
            fail("decrypting with the wrong master key must fail loud");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("decrypt"));
        }
    }

    @Test
    public void missingCredentialFailsLoud() {
        when(repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(7L, "org-gcs-creds"))
                .thenReturn(null);
        try {
            provider.getCredentials(organisation, "org-gcs-creds");
            fail("a missing credential must fail loud, not return null/garbage");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().contains("org-gcs-creds"));
        }
    }

    @Test
    public void blankCredentialRefFailsLoud() {
        try {
            provider.getCredentials(organisation, "  ");
            fail("a blank credentialRef must fail loud");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("credentialref"));
        }
    }

    @Test
    public void nullOrganisationFailsLoud() {
        try {
            provider.getCredentials(null, "org-gcs-creds");
            fail("resolving without an organisation must fail loud");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().toLowerCase().contains("organisation"));
        }
    }

    @Test
    public void blankMasterKeyConstructsButFailsLoudAtEncrypt() {
        EncryptedDbStorageCredentialProvider blankKeyProvider =
                new EncryptedDbStorageCredentialProvider(repository, cryptoService, "  ");
        try {
            blankKeyProvider.encryptSecret("super-secret-hmac-key");
            fail("encrypting with a blank master key must fail loud at point of use");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().contains("OPENCHS_STORAGE_CREDENTIALS_KEY"));
        } catch (Exception e) {
            fail("expected a StorageConfigurationException, got " + e.getClass().getName());
        }
    }

    @Test
    public void blankMasterKeyFailsLoudAtDecrypt() throws Exception {
        String encrypted = provider.encryptSecret("super-secret-hmac-key");
        when(repository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(7L, "org-gcs-creds"))
                .thenReturn(storedCredential("ACCESS123", encrypted));

        EncryptedDbStorageCredentialProvider blankKeyProvider =
                new EncryptedDbStorageCredentialProvider(repository, cryptoService, "");
        try {
            blankKeyProvider.getCredentials(organisation, "org-gcs-creds");
            fail("decrypting with a blank master key must fail loud at point of use");
        } catch (StorageConfigurationException e) {
            assertTrue(e.getMessage().contains("OPENCHS_STORAGE_CREDENTIALS_KEY"));
        }
    }
}

package org.avni.server.service;

import org.avni.server.dao.ModelKeyRepository;
import org.avni.server.domain.ModelKey;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.BadRequestError;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Server-only model key store (avniproject/avni-server#1020, D19/D20). Mirrors
 * {@code EncryptedDbStorageCredentialProviderTest}: encrypt-on-write / decrypt-on-read round-trip via
 * {@link CryptoService}; a wrong/blank master key fails loud at point-of-use (key decrypt is
 * load-bearing); construction with a blank master key must NOT throw (server boots fine); RLS/org
 * scoping (org A cannot read org B's key); absent key returns null (caller -> clean 404).
 */
public class ModelKeyServiceTest {

    // valid AES-256 base64 key (32 bytes)
    private static final String MASTER_KEY = "xqtzQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";
    private static final String OTHER_MASTER_KEY = "AAAAQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";

    private static final long ORG_A_ID = 11L;
    private static final long ORG_B_ID = 22L;
    // a valid 64-char hex SHA-256 digest (the store now validates the shape before any lookup)
    private static final String SHA256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff00";
    // a valid 64-char hex sha that resolves to no stored key (for the clean-404 path)
    private static final String ABSENT_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000";
    // the model's real AES key (base64) the device must receive unmasked
    private static final String REAL_MODEL_KEY = "Zm9vYmFyMTIzNDU2Nzg5MGFiY2RlZmdoaWprbA==";

    @Mock
    private ModelKeyRepository repository;

    private final CryptoService cryptoService = new CryptoService();
    private ModelKeyService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new ModelKeyService(repository, cryptoService, MASTER_KEY);
        when(repository.save(any(ModelKey.class))).thenAnswer(inv -> inv.getArgument(0));
        setOrg(ORG_A_ID);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    private void setOrg(long orgId) {
        Organisation organisation = new TestOrganisationBuilder().setId(orgId).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);
    }

    private ModelKey stored(String encryptedKey) {
        ModelKey modelKey = new ModelKey();
        modelKey.setSha256(SHA256);
        modelKey.setEncryptedKey(encryptedKey);
        return modelKey;
    }

    @Test
    public void encryptOnWriteDecryptOnReadRoundTripsTheRealKey() {
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);

        assertNotEquals("the stored key must be ciphertext, not the plaintext key",
                REAL_MODEL_KEY, saved.getEncryptedKey());

        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));

        // D19 deviation: read returns the REAL (unmasked) key, not a masked one.
        assertEquals("decrypt must recover the original key bytes unmasked",
                REAL_MODEL_KEY, service.getDecryptedKey(SHA256));
    }

    @Test
    public void wrongMasterKeyFailsLoudAtDecrypt() {
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));

        ModelKeyService wrongKeyService = new ModelKeyService(repository, cryptoService, OTHER_MASTER_KEY);
        try {
            wrongKeyService.getDecryptedKey(SHA256);
            fail("decrypting with the wrong master key must fail loud");
        } catch (ModelKeyException e) {
            assertTrue(e.getMessage().toLowerCase().contains("decrypt"));
        }
    }

    @Test
    public void blankMasterKeyConstructsButFailsLoudAtEncrypt() {
        // The server must boot fine when the master key is unset (F2 lesson) - construction must not throw...
        ModelKeyService blankKeyService = new ModelKeyService(repository, cryptoService, "  ");
        // ...but the first encrypt (point of use) must fail loud with a clear, actionable error.
        try {
            blankKeyService.storeKey(SHA256, REAL_MODEL_KEY);
            fail("encrypting with a blank master key must fail loud at point of use");
        } catch (ModelKeyException e) {
            assertTrue(e.getMessage().contains("OPENCHS_MODEL_KEY_ENCRYPTION_KEY"));
        }
    }

    @Test
    public void blankMasterKeyFailsLoudAtDecrypt() {
        // Pre-existing ciphertext (encrypted under a real key) but the deploy now has no master key.
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));

        ModelKeyService blankKeyService = new ModelKeyService(repository, cryptoService, "");
        try {
            blankKeyService.getDecryptedKey(SHA256);
            fail("decrypting with a blank master key must fail loud at point of use");
        } catch (ModelKeyException e) {
            assertTrue(e.getMessage().contains("OPENCHS_MODEL_KEY_ENCRYPTION_KEY"));
        }
    }

    @Test
    public void absentKeyReturnsNullForCleanFourOhFour() {
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, ABSENT_SHA256))
                .thenReturn(null);
        assertNull("absent key must return null (caller turns into a clean 404, not a 500)",
                service.getDecryptedKey(ABSENT_SHA256));
    }

    @Test
    public void blankSha256OnReadIsABadRequest() {
        // A blank required param is a client error -> clean 400 (BadRequestError), not an opaque path.
        try {
            service.getDecryptedKey("  ");
            fail("a blank sha256 must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("required"));
        }
    }

    @Test
    public void malformedSha256OnReadIsABadRequestAndDoesNotEchoTheRawValue() {
        // Non-64-hex value -> clean 400; the raw client-supplied value must NOT be echoed in the message.
        String malformed = "not-a-valid-hex-sha256";
        try {
            service.getDecryptedKey(malformed);
            fail("a malformed sha256 must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue("message must explain the malformation",
                    e.getMessage().toLowerCase().contains("malformed"));
            assertTrue("the raw client-supplied sha256 must not be echoed to the caller",
                    !e.getMessage().contains(malformed));
        }
    }

    @Test
    public void malformedSha256OnWriteIsABadRequest() {
        try {
            service.storeKey("too-short", REAL_MODEL_KEY);
            fail("a malformed sha256 on write must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("malformed"));
        }
    }

    @Test
    public void storeRejectsBlankKeyWithBadRequest() {
        // Client input validation -> 400 (BadRequestError), not a 500/Bugsnag.
        try {
            service.storeKey(SHA256, "  ");
            fail("storing a blank key must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("blank"));
        }
    }

    @Test
    public void cryptoFaultMessageDoesNotLeakMasterKeyPropOrSha256() {
        // Server crypto fault (wrong master key on decrypt): the ModelKeyException message returned to
        // the caller must be generic - no master-key property name, no raw sha256.
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));
        ModelKeyService wrongKeyService = new ModelKeyService(repository, cryptoService, OTHER_MASTER_KEY);
        try {
            wrongKeyService.getDecryptedKey(SHA256);
            fail("decrypting with the wrong master key must fail loud");
        } catch (ModelKeyException e) {
            assertTrue("must not leak the master-key property name",
                    !e.getMessage().contains("avni.model.key.base64EncodedEncryptionKey"));
            assertTrue("must not echo the raw sha256", !e.getMessage().contains(SHA256));
        }
    }

    @Test
    public void rlsOrgScopingOrgACannotReadOrgBKey() {
        // Org A's key is stored under (ORG_A_ID, SHA256). Org B asking for the same sha256 must not see it
        // because the lookup is scoped to the current org from UserContextHolder.
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_B_ID, SHA256))
                .thenReturn(null);

        // Org A reads its own key fine.
        assertEquals(REAL_MODEL_KEY, service.getDecryptedKey(SHA256));

        // Switch the request context to org B - same sha256 resolves to nothing.
        setOrg(ORG_B_ID);
        assertNull("org B must not read org A's key", service.getDecryptedKey(SHA256));
    }
}

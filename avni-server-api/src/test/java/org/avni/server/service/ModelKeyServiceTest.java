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

public class ModelKeyServiceTest {

    private static final String MASTER_KEY = "xqtzQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";
    private static final String OTHER_MASTER_KEY = "AAAAQhHsDFVQt9TK50UHcKda7/QM31bEE2lvTrcFoTU=";

    private static final long ORG_A_ID = 11L;
    private static final long ORG_B_ID = 22L;
    private static final String SHA256 = "a1b2c3d4e5f60718293a4b5c6d7e8f90112233445566778899aabbccddeeff00";
    private static final String ABSENT_SHA256 = "0000000000000000000000000000000000000000000000000000000000000000";
    // base64 of 32 raw bytes: a valid AES-256 key
    private static final String REAL_MODEL_KEY = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=";

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
        // construction with a blank master key must not throw (server boots fine); first encrypt must fail loud
        ModelKeyService blankKeyService = new ModelKeyService(repository, cryptoService, "  ");
        try {
            blankKeyService.storeKey(SHA256, REAL_MODEL_KEY);
            fail("encrypting with a blank master key must fail loud at point of use");
        } catch (ModelKeyException e) {
            assertTrue(e.getMessage().contains("OPENCHS_MODEL_KEY_ENCRYPTION_KEY"));
        }
    }

    @Test
    public void blankMasterKeyFailsLoudAtDecrypt() {
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
        try {
            service.getDecryptedKey("  ");
            fail("a blank sha256 must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("required"));
        }
    }

    @Test
    public void malformedSha256OnReadIsABadRequestAndDoesNotEchoTheRawValue() {
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
        try {
            service.storeKey(SHA256, "  ");
            fail("storing a blank key must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("blank"));
        }
    }

    @Test
    public void storeAcceptsValidAes256Key() {
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        assertNotEquals("a valid 32-byte AES key must be accepted and stored as ciphertext",
                REAL_MODEL_KEY, saved.getEncryptedKey());
    }

    @Test
    public void storeRejectsNonBase64KeyWithBadRequest() {
        try {
            service.storeKey(SHA256, "not base64 @@@");
            fail("a non-base64 key must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("base64"));
        }
    }

    @Test
    public void storeRejectsWrongLengthKeyWithBadRequest() {
        String tenByteKey = "MDEyMzQ1Njc4OQ==";
        try {
            service.storeKey(SHA256, tenByteKey);
            fail("a key that is not 16, 24, or 32 bytes must be a clean 400");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("16, 24, or 32 bytes"));
        }
    }

    @Test
    public void cryptoFaultMessageDoesNotLeakMasterKeyPropOrSha256() {
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
        ModelKey saved = service.storeKey(SHA256, REAL_MODEL_KEY);
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_A_ID, SHA256))
                .thenReturn(stored(saved.getEncryptedKey()));
        when(repository.findByOrganisationIdAndSha256AndIsVoidedFalse(ORG_B_ID, SHA256))
                .thenReturn(null);

        assertEquals(REAL_MODEL_KEY, service.getDecryptedKey(SHA256));

        setOrg(ORG_B_ID);
        assertNull("org B must not read org A's key", service.getDecryptedKey(SHA256));
    }
}

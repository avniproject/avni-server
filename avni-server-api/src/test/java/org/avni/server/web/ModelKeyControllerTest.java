package org.avni.server.web;

import org.avni.server.domain.Organisation;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ModelKeyException;
import org.avni.server.service.ModelKeyService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.ModelKeyRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * Device key-delivery endpoint behaviour (avniproject/avni-server#1020, D19/D20): returns the REAL
 * (unmasked) key to a {@code 'user'} caller; an absent sha256 yields a clean 404 (not a 500); the key
 * is never echoed by the write path.
 */
public class ModelKeyControllerTest {

    private static final String SHA256 = "abc123sha256";
    private static final String REAL_MODEL_KEY = "Zm9vYmFyMTIzNDU2Nzg5MGFiY2RlZmdoaWprbA==";

    @Mock
    private ModelKeyService modelKeyService;
    @Mock
    private AccessControlService accessControlService;

    private ModelKeyController controller;

    @Before
    public void setUp() {
        initMocks(this);
        controller = new ModelKeyController(modelKeyService, accessControlService);
        // The device read path emits an audit log via UserContextHolder, so a context must be present.
        Organisation organisation = new TestOrganisationBuilder().setId(1L).build();
        User user = new User();
        user.setId(7L);
        user.setUsername("device-user@org");
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        userContext.setUser(user);
        UserContextHolder.create(userContext);
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void deviceEndpointReturnsTheRealUnmaskedKey() {
        when(modelKeyService.getDecryptedKey(SHA256)).thenReturn(REAL_MODEL_KEY);

        ResponseEntity<String> response = controller.getModelKey(SHA256);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // D19 deviation from Msg91: the body is the REAL key, not a masked one.
        assertEquals(REAL_MODEL_KEY, response.getBody());
    }

    @Test
    public void absentKeyYieldsCleanFourOhFour() {
        when(modelKeyService.getDecryptedKey("no-such-sha")).thenReturn(null);

        ResponseEntity<String> response = controller.getModelKey("no-such-sha");

        assertEquals("absent key must be a clean 404, not a 500", HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    public void writePathStoresButNeverEchoesTheKey() {
        ModelKeyRequest request = new ModelKeyRequest();
        request.setSha256(SHA256);
        request.setKey(REAL_MODEL_KEY);

        ResponseEntity<?> response = controller.storeKey(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        // the write path must not return the key (not even masked) in the body.
        assertNull("the write response must not echo the key", response.getBody());
        verify(modelKeyService).storeKey(SHA256, REAL_MODEL_KEY);
    }

    @Test
    public void writePathPropagatesBadRequestForClientInputErrors() {
        // Client input validation (blank/malformed sha256 or blank key) surfaces as BadRequestError,
        // which the global ErrorInterceptors maps to a clean 400 - the controller must NOT swallow it
        // into a 500/Bugsnag path.
        ModelKeyRequest request = new ModelKeyRequest();
        request.setSha256("bad");
        request.setKey("");
        doThrow(new BadRequestError("Cannot store a blank model key"))
                .when(modelKeyService).storeKey("bad", "");
        try {
            controller.storeKey(request);
            fail("a client input error must propagate as BadRequestError (-> 400 via ErrorInterceptors)");
        } catch (BadRequestError expected) {
            // ErrorInterceptors.unknownException() turns this into a 400.
        }
    }

    @Test
    public void serverFaultMapsToCleanFiveHundredWithoutLeakingConfig() {
        // A crypto/config fault (ModelKeyException) maps to a clean 5xx via the controller's
        // @ExceptionHandler - the body must NOT echo the master-key property name or internal config.
        ModelKeyException fault = new ModelKeyException("Failed to decrypt the model key");

        ResponseEntity<String> response = controller.modelKeyServerFault(fault);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertFalse("the 5xx body must not leak the master-key property name",
                response.getBody().contains("avni.model.key.base64EncodedEncryptionKey"));
        assertFalse("the 5xx body must not leak the env var name",
                response.getBody().contains("OPENCHS_MODEL_KEY_ENCRYPTION_KEY"));
    }
}

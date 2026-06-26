package org.avni.server.web;

import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ModelKeyException;
import org.avni.server.service.ModelKeyService;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.web.request.ModelKeyRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Server-only model key store endpoints (avniproject/avni-server#1020, D19/D20). Sits alongside
 * {@link MediaController} because the key delivery rides the device {@code /media} surface that already
 * serves the model blob.
 * <p>
 * The model's AES key lives only in the encrypted-at-rest key store ({@code ModelKey}). It is
 * <b>never</b> written to a subject/ref-data observation, never returned in any {@code /web/...}
 * response, and not shipped in the APK.
 */
@RestController
public class ModelKeyController {
    private final Logger logger;
    private final ModelKeyService modelKeyService;
    private final AccessControlService accessControlService;

    @Autowired
    public ModelKeyController(ModelKeyService modelKeyService, AccessControlService accessControlService) {
        this.modelKeyService = modelKeyService;
        this.accessControlService = accessControlService;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    /**
     * Ops/admin write path: stores (encrypting on write) the AES key for a {@code sha256}. The key is
     * never echoed back. Privileged - only org admins/ops can set it.
     */
    @PostMapping(value = "/web/modelKey")
    @Transactional
    public ResponseEntity<?> storeKey(@RequestBody ModelKeyRequest request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        modelKeyService.storeKey(request.getSha256(), request.getKey());
        // Client input errors (blank/malformed sha256 or blank key) surface as BadRequestError ->
        // mapped to a clean 400 by ErrorInterceptors. Crypto/config faults surface as ModelKeyException
        // -> mapped to a clean 5xx by modelKeyServerFault() below (no master-key prop name leaked).
        // Deliberately do NOT return the key (not even masked) - write is fire-and-forget.
        return ResponseEntity.ok().build();
    }

    /**
     * Device key-delivery endpoint (D19). Mirrors {@code Msg91ConfigController.getConfiguration()} BUT,
     * unlike Msg91 (which masks the secret), this deliberately returns the REAL (unmasked) key - the
     * device needs the actual bytes to decrypt the model.
     * <p>
     * Posture caveat (D20): this is "not surfaced to web/DEA", but it is NOT cryptographically
     * android-only. Avni has no client-type gate, so any authenticated {@code 'user'} can call this. The
     * key is kept off the web/ref-data surface, which is an exposure reduction, not hard isolation.
     * <p>
     * Org-scoped via {@code UserContextHolder} inside {@link ModelKeyService}. An absent key for the
     * sha256 yields a clean 404 (not a 500).
     */
    @RequestMapping(value = "/media/modelKey", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getModelKey(@RequestParam String sha256) {
        String key = modelKeyService.getDecryptedKey(sha256);
        if (key == null) {
            // Absent key -> clean 404, not a 500 (the blob may exist before its key is provisioned).
            return ResponseEntity.notFound().build();
        }
        // Audit: record WHO fetched WHICH key for WHICH org. Never log the key itself.
        User user = UserContextHolder.getUserContext().getUser();
        logger.info("Model key fetched: userId={}, username={}, org={}, sha256={}",
                user == null ? null : user.getId(),
                UserContextHolder.getUserContext().getUserName(),
                UserContextHolder.getUserContext().getOrganisationName(),
                sha256);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(key);
    }

    /**
     * Server-side crypto/config fault (blank/incorrect deploy master key, decrypt failure). Returns a
     * clean 5xx with a generic message - the real detail (incl. the master-key property name and the
     * sha256) is logged inside {@link ModelKeyService}, never echoed to the caller. Plain client input
     * errors do NOT reach here (they are BadRequestError -> 400), so this avoids a Bugsnag storm / 500
     * for validation errors.
     */
    @ExceptionHandler(ModelKeyException.class)
    public ResponseEntity<String> modelKeyServerFault(ModelKeyException e) {
        logger.error("Model key store server fault", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Model key could not be processed due to a server configuration error.");
    }
}

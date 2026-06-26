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

    @PostMapping(value = "/web/modelKey")
    @Transactional
    public ResponseEntity<?> storeKey(@RequestBody ModelKeyRequest request) {
        accessControlService.checkPrivilege(PrivilegeType.EditOrganisationConfiguration);
        modelKeyService.storeKey(request.getSha256(), request.getKey());
        return ResponseEntity.ok().build();
    }

    // Device key-delivery endpoint: returns the REAL (unmasked) key (unlike Msg91), org-scoped, 404 when absent.
    @RequestMapping(value = "/media/modelKey", method = RequestMethod.GET)
    @PreAuthorize(value = "hasAnyAuthority('user')")
    @Transactional(readOnly = true)
    public ResponseEntity<String> getModelKey(@RequestParam String sha256) {
        String key = modelKeyService.getDecryptedKey(sha256);
        if (key == null) {
            return ResponseEntity.notFound().build();
        }
        // audit who fetched which key; never log the key itself
        User user = UserContextHolder.getUserContext().getUser();
        logger.info("Model key fetched: userId={}, username={}, org={}, sha256={}",
                user == null ? null : user.getId(),
                UserContextHolder.getUserContext().getUserName(),
                UserContextHolder.getUserContext().getOrganisationName(),
                sha256);
        return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(key);
    }

    // Crypto/config fault -> generic 5xx; the real detail is logged in ModelKeyService, never echoed to the caller.
    @ExceptionHandler(ModelKeyException.class)
    public ResponseEntity<String> modelKeyServerFault(ModelKeyException e) {
        logger.error("Model key store server fault", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Model key could not be processed due to a server configuration error.");
    }
}

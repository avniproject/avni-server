package org.avni.server.web.abdm;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.*;
import org.avni.server.service.abdm.gateway.AbdmCallbackVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

// DEMO-ONLY abdm-hackathon: never merged.
// Every path the gateway (or the mock, or an HIP pushing data) calls on us. Each answers 202 at once and does the work
// on an AbdmAsync thread as the organisation's system user: log the message, then hand it to the HIP or HIU handler.
// No Avni user: these paths are unprotected (ResourceProtectionStatus) and checked by AbdmCallbackVerifier instead,
// except the data push, which an HIP sends without a gateway token.
@RestController
public class AbdmCallbackController {
    private static final Logger logger = LoggerFactory.getLogger(AbdmCallbackController.class);

    private final AbdmCallbackVerifier verifier;
    private final AbdmAsync abdmAsync;
    private final AbdmMessageLog messageLog;
    private final ObjectProvider<AbdmHipCallbacks> hip;
    private final ObjectProvider<AbdmHiuCallbacks> hiu;

    @FunctionalInterface
    interface Handler<T> {
        void handle(T target, JsonNode body, AbdmCallbackHeaders headers);
    }

    @Autowired
    public AbdmCallbackController(AbdmCallbackVerifier verifier, AbdmAsync abdmAsync, AbdmMessageLog messageLog,
                                  ObjectProvider<AbdmHipCallbacks> hip, ObjectProvider<AbdmHiuCallbacks> hiu) {
        this.verifier = verifier;
        this.abdmAsync = abdmAsync;
        this.messageLog = messageLog;
        this.hip = hip;
        this.hiu = hiu;
    }

    @PostMapping(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN)
    public ResponseEntity<Void> onGenerateToken(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_ON_GENERATE_TOKEN, body, headers, true, hip, AbdmHipCallbacks::onGenerateToken);
    }

    @PostMapping(AbdmPaths.IN_HIP_ON_LINK_CARE_CONTEXT)
    public ResponseEntity<Void> onLinkCareContext(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_ON_LINK_CARE_CONTEXT, body, headers, true, hip, AbdmHipCallbacks::onLinkCareContext);
    }

    @PostMapping(AbdmPaths.IN_HIP_DISCOVER)
    public ResponseEntity<Void> discover(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_DISCOVER, body, headers, true, hip, AbdmHipCallbacks::discover);
    }

    @PostMapping(AbdmPaths.IN_HIP_LINK_INIT)
    public ResponseEntity<Void> linkInit(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_LINK_INIT, body, headers, true, hip, AbdmHipCallbacks::linkInit);
    }

    @PostMapping(AbdmPaths.IN_HIP_LINK_CONFIRM)
    public ResponseEntity<Void> linkConfirm(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_LINK_CONFIRM, body, headers, true, hip, AbdmHipCallbacks::linkConfirm);
    }

    @PostMapping(AbdmPaths.IN_HIP_CONSENT_NOTIFY)
    public ResponseEntity<Void> hipConsentNotify(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_CONSENT_NOTIFY, body, headers, true, hip, AbdmHipCallbacks::consentNotify);
    }

    @PostMapping(AbdmPaths.IN_HIP_HEALTH_INFORMATION_REQUEST)
    public ResponseEntity<Void> healthInformationRequest(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIP_HEALTH_INFORMATION_REQUEST, body, headers, true, hip, AbdmHipCallbacks::healthInformationRequest);
    }

    @PostMapping(AbdmPaths.IN_HIU_CONSENT_ON_INIT)
    public ResponseEntity<Void> consentRequestOnInit(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_CONSENT_ON_INIT, body, headers, true, hiu, AbdmHiuCallbacks::consentRequestOnInit);
    }

    @PostMapping(AbdmPaths.IN_HIU_CONSENT_NOTIFY)
    public ResponseEntity<Void> hiuConsentNotify(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_CONSENT_NOTIFY, body, headers, true, hiu, AbdmHiuCallbacks::consentRequestNotify);
    }

    @PostMapping(AbdmPaths.IN_HIU_CONSENT_ON_STATUS)
    public ResponseEntity<Void> consentRequestOnStatus(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_CONSENT_ON_STATUS, body, headers, true, hiu, AbdmHiuCallbacks::consentRequestOnStatus);
    }

    @PostMapping(AbdmPaths.IN_HIU_CONSENT_ON_FETCH)
    public ResponseEntity<Void> consentOnFetch(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_CONSENT_ON_FETCH, body, headers, true, hiu, AbdmHiuCallbacks::consentOnFetch);
    }

    @PostMapping(AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST)
    public ResponseEntity<Void> healthInformationOnRequest(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_HEALTH_INFORMATION_ON_REQUEST, body, headers, true, hiu, AbdmHiuCallbacks::healthInformationOnRequest);
    }

    // An HIP's push to our dataPushUrl: no gateway token, so no signature check.
    @PostMapping(AbdmPaths.IN_HIU_DATA_PUSH)
    public ResponseEntity<Void> dataPush(@RequestBody JsonNode body, @RequestHeader HttpHeaders headers) {
        return accept(AbdmPaths.IN_HIU_DATA_PUSH, body, headers, false, hiu, AbdmHiuCallbacks::dataPush);
    }

    private <T> ResponseEntity<Void> accept(String path, JsonNode body, HttpHeaders httpHeaders, boolean verify,
                                            ObjectProvider<T> handlers, Handler<T> handler) {
        AbdmCallbackHeaders h = AbdmCallbackHeaders.from(path, httpHeaders);
        if (verify) {
            try {
                verifier.check(h);
            } catch (AbdmCallbackVerifier.CallbackRejected e) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
        }
        abdmAsync.submit(path, () -> {
            messageLog.incoming(path, h, body);
            T target = handlers.getIfAvailable();
            if (target == null) {
                logger.warn("ABDM callback {} (request {}) has no handler; logged only", path, h.requestId());
                return;
            }
            handler.handle(target, body, h);
        });
        return ResponseEntity.accepted().build();
    }
}

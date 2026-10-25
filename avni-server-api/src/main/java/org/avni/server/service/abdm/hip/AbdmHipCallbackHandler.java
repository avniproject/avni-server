package org.avni.server.service.abdm.hip;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmHipCallbacks;
import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// The gateway's calls on us as the HIP, handed to the service that owns each flow.
@Component
public class AbdmHipCallbackHandler implements AbdmHipCallbacks {
    private final AbdmHipLinkingService linkingService;
    private final AbdmHipDiscoveryService discoveryService;
    private final AbdmHipConsentService consentService;
    private final AbdmHipDataTransferService dataTransferService;

    public AbdmHipCallbackHandler(AbdmHipLinkingService linkingService, AbdmHipDiscoveryService discoveryService,
                                  AbdmHipConsentService consentService, AbdmHipDataTransferService dataTransferService) {
        this.linkingService = linkingService;
        this.discoveryService = discoveryService;
        this.consentService = consentService;
        this.dataTransferService = dataTransferService;
    }

    @Override
    public void onGenerateToken(JsonNode body, AbdmCallbackHeaders headers) {
        linkingService.onGenerateToken(body, headers);
    }

    @Override
    public void onLinkCareContext(JsonNode body, AbdmCallbackHeaders headers) {
        linkingService.onLinkCareContext(body, headers);
    }

    @Override
    public void discover(JsonNode body, AbdmCallbackHeaders headers) {
        discoveryService.discover(body, headers);
    }

    @Override
    public void linkInit(JsonNode body, AbdmCallbackHeaders headers) {
        discoveryService.linkInit(body, headers);
    }

    @Override
    public void linkConfirm(JsonNode body, AbdmCallbackHeaders headers) {
        discoveryService.linkConfirm(body, headers);
    }

    @Override
    public void consentNotify(JsonNode body, AbdmCallbackHeaders headers) {
        consentService.consentNotify(body, headers);
    }

    @Override
    public void healthInformationRequest(JsonNode body, AbdmCallbackHeaders headers) {
        dataTransferService.healthInformationRequest(body, headers);
    }
}

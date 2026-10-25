package org.avni.server.service.abdm.hiu;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmCallbackHeaders;
import org.avni.server.service.abdm.AbdmHiuCallbacks;
import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// The gateway's (and HIPs') calls on us as the HIU, handed to the service that owns each flow.
@Component
public class AbdmHiuCallbackHandler implements AbdmHiuCallbacks {
    private final AbdmHiuConsentService consentService;
    private final AbdmHiuDataService dataService;

    public AbdmHiuCallbackHandler(AbdmHiuConsentService consentService, AbdmHiuDataService dataService) {
        this.consentService = consentService;
        this.dataService = dataService;
    }

    @Override
    public void consentRequestOnInit(JsonNode body, AbdmCallbackHeaders headers) {
        consentService.onInit(body, headers);
    }

    @Override
    public void consentRequestNotify(JsonNode body, AbdmCallbackHeaders headers) {
        consentService.notify(body, headers);
    }

    @Override
    public void consentRequestOnStatus(JsonNode body, AbdmCallbackHeaders headers) {
        consentService.onStatus(body, headers);
    }

    @Override
    public void consentOnFetch(JsonNode body, AbdmCallbackHeaders headers) {
        consentService.onFetch(body, headers);
    }

    @Override
    public void healthInformationOnRequest(JsonNode body, AbdmCallbackHeaders headers) {
        dataService.onRequest(body, headers);
    }

    @Override
    public void dataPush(JsonNode body, AbdmCallbackHeaders headers) {
        dataService.dataPush(body, headers);
    }
}

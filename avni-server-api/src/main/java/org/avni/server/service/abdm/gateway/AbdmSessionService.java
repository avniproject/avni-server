package org.avni.server.service.abdm.gateway;

import org.springframework.stereotype.Component;

// DEMO-ONLY abdm-hackathon: never merged.
// The bridge's gateway session token, cached until close to expiry. Never logs the token or the client secret.
@Component
public class AbdmSessionService {
    public String accessToken() {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }

    public void invalidate() {
        throw new UnsupportedOperationException("abdm-hackathon: not built yet");
    }
}

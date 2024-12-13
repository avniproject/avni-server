package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificAuthRequest {
    private final GlificUser user;

    public GlificAuthRequest(GlificUser user) {
        this.user = user;
    }

    public GlificUser getUser() {
        return user;
    }
}

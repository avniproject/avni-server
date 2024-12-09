package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificOptinContactResponse {
    private GlificOptinContactWithErrorsResponse optinContact;

    public GlificOptinContactWithErrorsResponse getOptinContact() {
        return optinContact;
    }

    public void setOptinContact(GlificOptinContactWithErrorsResponse optinContact) {
        this.optinContact = optinContact;
    }
}

package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificOptinContactWithErrorsResponse {

    private GlificContactResponse contact;

    public GlificContactResponse getContact() {
        return contact;
    }

    public void setContact(GlificContactResponse contact) {
        this.contact = contact;
    }
}

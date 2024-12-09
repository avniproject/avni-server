package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificGetContactsResponse {
    private List<GlificContactResponse> contacts;

    public List<GlificContactResponse> getContacts() {
        return contacts;
    }

    public void setContacts(List<GlificContactResponse> contacts) {
        this.contacts = contacts;
    }
}

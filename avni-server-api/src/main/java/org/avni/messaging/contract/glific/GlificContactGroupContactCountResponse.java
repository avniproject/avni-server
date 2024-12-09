package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificContactGroupContactCountResponse {
    private int countContacts;

    public int getCountContacts() {
        return countContacts;
    }

    public void setCountContacts(int countContacts) {
        this.countContacts = countContacts;
    }
}

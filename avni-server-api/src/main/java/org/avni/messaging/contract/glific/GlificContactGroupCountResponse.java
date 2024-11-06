package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificContactGroupCountResponse {
    private int countGroups;

    public int getCountGroups() {
        return countGroups;
    }

    public void setCountGroups(int countGroups) {
        this.countGroups = countGroups;
    }
}

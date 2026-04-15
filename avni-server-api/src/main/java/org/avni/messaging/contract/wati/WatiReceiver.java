package org.avni.messaging.contract.wati;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * A single receiver entry in the Wati sendTemplateMessages API.
 * The phone number and per-receiver parameters go here.
 */
public class WatiReceiver {

    @JsonProperty("whatsappNumber")
    private String whatsappNumber;

    @JsonProperty("customParams")
    private List<WatiParameter> customParams;

    public WatiReceiver(String whatsappNumber, List<WatiParameter> customParams) {
        this.whatsappNumber = whatsappNumber;
        this.customParams = customParams;
    }

    public String getWhatsappNumber() {
        return whatsappNumber;
    }

    public List<WatiParameter> getCustomParams() {
        return customParams;
    }
}

package org.avni.messaging.contract.wati;

/**
 * Represents a single parameter in a Wati template message receiver.
 * Wati's sendTemplateMessages API uses "name" and "value" inside customParams.
 *
 * Example: {"name": "1", "value": "Ramesh Kumar"}
 */
public class WatiParameter {

    private String name;
    private String value;

    public WatiParameter(String name, String value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}

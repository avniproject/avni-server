package org.avni.server.web.contract;

import java.io.Serializable;
import java.util.LinkedHashMap;

public class ValueUnit implements Serializable {
    private String value;
    private String unit;

    public ValueUnit() {}

    public ValueUnit(String valueUnit) {}

    public ValueUnit(String value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    public ValueUnit(LinkedHashMap<String, String> valueUnit) {
        this.value = valueUnit.get("value");
        this.unit = valueUnit.get("unit");
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String toJSONString() {
        return String.format("{\"%s\":\"%s\", \"%s\":\"%s\"}", "value", value, "unit", unit);
    }
}

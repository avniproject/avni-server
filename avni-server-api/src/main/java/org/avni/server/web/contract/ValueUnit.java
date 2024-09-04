package org.avni.server.web.contract;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.avni.server.util.ObjectMapperSingleton;

import java.io.Serializable;

public class ValueUnit implements Serializable {
    private String value;
    private String unit;

    public ValueUnit() {}

    public ValueUnit(String valueUnit) {}

    public ValueUnit(String value, String unit) {
        this.value = value;
        this.unit = unit;
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
        try {
            return ObjectMapperSingleton.getObjectMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}

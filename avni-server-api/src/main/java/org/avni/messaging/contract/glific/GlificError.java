package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificError {
    private List<GlificErrorLocation> locations;
    private String message;

    public GlificError() {
    }

    public List<GlificErrorLocation> getLocations() {
        return locations;
    }

    public void setLocations(List<GlificErrorLocation> locations) {
        this.locations = locations;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "GlificError{" +
                "locations=" + locations +
                ", message='" + message + '\'' +
                '}';
    }
}

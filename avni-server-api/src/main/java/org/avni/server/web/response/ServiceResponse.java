package org.avni.server.web.response;

import org.avni.server.config.AvniServiceType;

public class ServiceResponse {
    private AvniServiceType serviceType;
    private String origin;

    public AvniServiceType getServiceType() {
        return serviceType;
    }

    public void setServiceType(AvniServiceType serviceType) {
        this.serviceType = serviceType;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }
}

package org.avni.server.web;

import org.avni.server.config.AvniServiceType;
import org.avni.server.web.response.ServiceResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

@RestController
public class ServiceController {
    @Value("${avni.etl.service.origin}")
    private String avniEtlServiceOrigin;

    @GetMapping("service")
    public List<ServiceResponse> getServices() {
        ServiceResponse serviceResponse = new ServiceResponse();
        serviceResponse.setOrigin(avniEtlServiceOrigin);
        serviceResponse.setServiceType(AvniServiceType.ETL);
        return Collections.singletonList(serviceResponse);
    }
}

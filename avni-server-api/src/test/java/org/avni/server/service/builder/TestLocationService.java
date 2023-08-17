package org.avni.server.service.builder;

import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class TestLocationService {
    private final LocationRepository locationRepository;

    @Autowired
    public TestLocationService(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    public AddressLevel save(AddressLevel location) {
        locationRepository.save(location);
        location.calculateLineage();
        locationRepository.save(location);
        return location;
    }
}

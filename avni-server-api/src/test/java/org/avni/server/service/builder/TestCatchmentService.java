package org.avni.server.service.builder;

import org.avni.server.dao.CatchmentRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Catchment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TestCatchmentService {
    private final CatchmentRepository catchmentRepository;
    private final LocationRepository locationRepository;

    @Autowired
    public TestCatchmentService(CatchmentRepository catchmentRepository, LocationRepository locationRepository) {
        this.catchmentRepository = catchmentRepository;
        this.locationRepository = locationRepository;
    }

    public void createCatchment(Catchment catchment, AddressLevel ... addressLevels) {
        catchment.setAddressLevels(Arrays.stream(addressLevels).collect(Collectors.toSet()));
        catchmentRepository.save(catchment);
        Arrays.stream(addressLevels).forEach(addressLevel -> {
            Set<Catchment> catchments = addressLevel.getCatchments();
            catchments.add(catchment);
            locationRepository.save(addressLevel);
        });
    }
}

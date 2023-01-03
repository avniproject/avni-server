package org.avni.server.service;

import org.avni.server.application.projections.VirtualCatchmentProjection;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.Catchment;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class AddressLevelCache {
    public static final String ADDRESSES_PER_CATCHMENT = "addressesPerCatchment";
    public static final String ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS = "addressesPerCatchmentAndMatchingAddrLevels" +
            "";;

    private final LocationRepository locationRepository;

    public AddressLevelCache(LocationRepository locationRepository) {
        this.locationRepository = locationRepository;
    }

    @Cacheable(value = ADDRESSES_PER_CATCHMENT)
    public List<VirtualCatchmentProjection> getAddressLevelsForCatchment(Catchment catchment) {
        return locationRepository.getVirtualCatchmentsForCatchmentId(catchment.getId());
    }

    @Cacheable(value = ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS)
    public List<VirtualCatchmentProjection> getAddressLevelsForCatchmentAndMatchingAddressLevelTypeIds(Catchment catchment, List<Long> matchingAddressLevelTypeIds) {
        return locationRepository.getVirtualCatchmentsForCatchmentId(catchment.getId()).stream()
                .filter(addressLevel -> matchingAddressLevelTypeIds.contains(addressLevel.getType_id())).collect(Collectors.toList());
    }
}
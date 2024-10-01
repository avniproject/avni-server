package org.avni.server.service;

import org.avni.server.application.projections.CatchmentAddressProjection;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.RoleSwitchableRepository;
import org.avni.server.domain.Catchment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.transaction.Transactional;
import java.util.List;

@Component
public class AddressLevelCache extends RoleSwitchableRepository {
    public static final String ADDRESSES_PER_CATCHMENT = "addressesPerCatchment";
    public static final String ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS = "addressesPerCatchmentAndMatchingAddrLevels";

    private final LocationRepository locationRepository;

    @Autowired
    public AddressLevelCache(EntityManager entityManager, LocationRepository locationRepository) {
        super(entityManager);
        this.locationRepository = locationRepository;
    }

    @Cacheable(value = ADDRESSES_PER_CATCHMENT)
    @Transactional
    public List<CatchmentAddressProjection> getAddressLevelsForCatchment(Catchment catchment) {
        try {
            setRoleToNone();
            return locationRepository.getCatchmentAddressesForCatchmentId(catchment.getId());
        } finally {
            setRoleBackToUser();
        }
    }

    @Cacheable(value = ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS)
    @Transactional
    public List<CatchmentAddressProjection> getAddressLevelsForCatchmentAndMatchingAddressLevelTypeIds(Catchment catchment, List<Long> matchingAddressLevelTypeIds) {
        try {
            setRoleToNone();
            return locationRepository.getCatchmentAddressesForCatchmentIdAndLocationTypeId(catchment.getId(), matchingAddressLevelTypeIds);
        } finally {
            setRoleBackToUser();
        }
    }
}

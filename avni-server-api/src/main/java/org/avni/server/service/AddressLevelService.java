package org.avni.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.KeyType;
import org.avni.server.application.projections.VirtualCatchmentProjection;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.AddressLevelContract;
import org.avni.server.web.request.webapp.SubjectTypeSetting;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AddressLevelService {
    private final LocationRepository locationRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationConfigService organisationConfigService;
    private ObjectMapper objectMapper;
    private ThreadLocal<AddressLevelsForCatchment> addressLevelCache = ThreadLocal.withInitial(AddressLevelsForCatchment::new);

    private class AddressLevelsForCatchment {
        private Long catchmentId;
        private List<VirtualCatchmentProjection> cachedAddressLevels;

        public List<Long> getAddressLevelsByCatchmentAndSubjectType(Catchment catchment, SubjectType subjectType) {
            ensureCacheExists(catchment);

            return filterBySubjectType(subjectType)
                    .map(VirtualCatchmentProjection::getAddresslevel_id)
                    .collect(Collectors.toList());
        }

        private void ensureCacheExists(Catchment catchment) {
            if (!Objects.equals(catchment.getId(), catchmentId)) {
                this.cachedAddressLevels = getAddressLevelsForCatchment(catchment);
                this.catchmentId = catchment.getId();
            }
        }

        private List<VirtualCatchmentProjection> getAddressLevelsForCatchment(Catchment catchment) {
            return locationRepository.getVirtualCatchmentsForCatchmentId(catchment.getId());
        }

        private Stream<VirtualCatchmentProjection> filterBySubjectType(SubjectType subjectType) {
            Optional<SubjectTypeSetting> customRegistrationLocationSetting = getCustomRegistrationSetting(subjectType);

            if (customRegistrationLocationSetting.isPresent() && !customRegistrationLocationSetting.get().getLocationTypeUUIDs().isEmpty()) {
                List<Long> matchingAddressLevelTypeIds = addressLevelTypeRepository.findAllByUuidIn(
                                customRegistrationLocationSetting.get().getLocationTypeUUIDs())
                        .stream()
                        .map(CHSBaseEntity::getId)
                        .collect(Collectors.toList());

                return this.cachedAddressLevels.stream()
                        .filter(addressLevel -> matchingAddressLevelTypeIds.contains(addressLevel.getType_id()));
            }

            return this.cachedAddressLevels.stream();
        }

        private Optional<SubjectTypeSetting> getCustomRegistrationSetting(SubjectType subjectType) {
            List<SubjectTypeSetting> customRegistrationLocations = objectMapper.convertValue(
                    organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString()),
                    new TypeReference<List<SubjectTypeSetting>>() {
                    });
            Optional<SubjectTypeSetting> customLocationTypes = customRegistrationLocations.stream()
                    .filter(crl -> crl.getSubjectTypeUUID()
                            .equals(subjectType.getUuid()))
                    .findFirst();
            return customLocationTypes;
        }
    }

    public AddressLevelService(LocationRepository locationRepository,
                               AddressLevelTypeRepository addressLevelTypeRepository, OrganisationConfigService organisationConfigService) {
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationConfigService = organisationConfigService;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public List<AddressLevelContract> getAllLocations() {
        List<AddressLevel> locationList = locationRepository.getAllByIsVoidedFalse();
        return locationList.stream()
                .filter(al -> !al.getType().isVoided())
                .map(addressLevel -> {
                    AddressLevelContract addressLevelContract = new AddressLevelContract();
                    addressLevelContract.setId(addressLevel.getId());
                    addressLevelContract.setUuid(addressLevel.getUuid());
                    addressLevelContract.setName(addressLevel.getTitle());
                    addressLevelContract.setType(addressLevel.getType().getName());
                    return addressLevelContract;
                }).collect(Collectors.toList());
    }

    public List<String> getAllAddressLevelTypeNames() {
        return addressLevelTypeRepository.findAllByIsVoidedFalse()
                .stream()
                .sorted(Comparator.comparingDouble(AddressLevelType::getLevel))
                .map(AddressLevelType::getName)
                .collect(Collectors.toList());
    }

    public List<Long> getAllRegistrationAddressIdsBySubjectType(Catchment catchment, SubjectType subjectType) {
        return addressLevelCache.get().getAddressLevelsByCatchmentAndSubjectType(catchment, subjectType);
    }

    public String getTitleLineage(AddressLevel location) {
        return locationRepository.getTitleLineageById(location.getId());
    }
}

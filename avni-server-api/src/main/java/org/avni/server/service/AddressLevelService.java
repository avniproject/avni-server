package org.avni.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.KeyType;
import org.avni.server.application.projections.CatchmentAddressProjection;
import org.avni.server.application.projections.LocationProjection;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.*;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.request.AddressLevelContract;
import org.avni.server.web.request.AddressLevelContractWeb;
import org.avni.server.web.request.webapp.SubjectTypeSetting;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class AddressLevelService {
    private final LocationRepository locationRepository;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelCache addressLevelCache;
    private final ObjectMapper objectMapper;
    private final LocationHierarchyService locationHierarchyService;

    public AddressLevelService(LocationRepository locationRepository,
                               AddressLevelTypeRepository addressLevelTypeRepository,
                               OrganisationConfigService organisationConfigService,
                               AddressLevelCache addressLevelCache, LocationHierarchyService locationHierarchyService) {
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationConfigService = organisationConfigService;
        this.addressLevelCache = addressLevelCache;
        this.locationHierarchyService = locationHierarchyService;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    public List<Long> getAddressLevelsByCatchmentAndSubjectType(Catchment catchment, SubjectType subjectType) {
        return filterByCatchmentAndSubjectType(catchment, subjectType)
                .map(CatchmentAddressProjection::getAddresslevel_id)
                .collect(Collectors.toList());
    }

    private Stream<CatchmentAddressProjection> filterByCatchmentAndSubjectType(Catchment catchment, SubjectType subjectType) {
        Optional<SubjectTypeSetting> customRegistrationLocationSetting = getCustomRegistrationSetting(subjectType);

        if (customRegistrationLocationSetting.isPresent() && !customRegistrationLocationSetting.get().getLocationTypeUUIDs().isEmpty()) {
            List<Long> matchingAddressLevelTypeIds = addressLevelTypeRepository.findAllByUuidIn(
                            customRegistrationLocationSetting.get().getLocationTypeUUIDs())
                    .stream()
                    .map(CHSBaseEntity::getId)
                    .sorted()
                    .collect(Collectors.toList());

            return addressLevelCache.getAddressLevelsForCatchmentAndMatchingAddressLevelTypeIds(catchment, matchingAddressLevelTypeIds).stream();
        }

        return addressLevelCache.getAddressLevelsForCatchment(catchment).stream();
    }

    private Optional<SubjectTypeSetting> getCustomRegistrationSetting(SubjectType subjectType) {
        List<SubjectTypeSetting> customRegistrationLocations = objectMapper.convertValue(
                organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString()),
                new TypeReference<List<SubjectTypeSetting>>() {
                });
        return customRegistrationLocations.stream()
                .filter(crl -> crl.getSubjectTypeUUID()
                        .equals(subjectType.getUuid()))
                .findFirst();
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
        return getAddressLevelsByCatchmentAndSubjectType(catchment, subjectType);
    }

    public String getTitleLineage(AddressLevel location) {
        return locationRepository.getTitleLineageById(location.getId());
    }

    public List<AddressLevelContractWeb> addTitleLineageToLocation(List<LocationProjection> locationProjections) {
        Map<Long, String> titleLineages = getTitleLineages(locationProjections.stream().map(LocationProjection::getId).collect(Collectors.toList()));
        return locationProjections.stream().map(locationProjection -> {
            AddressLevelContractWeb addressLevel = AddressLevelContractWeb.fromEntity(locationProjection);
            addressLevel.setTitleLineage(titleLineages.get(locationProjection.getId()));
            return addressLevel;
        }).collect(Collectors.toList());
    }

    public Page<AddressLevelContractWeb> addTitleLineageToLocation(Page<LocationProjection> locationProjections) {
        List<AddressLevelContractWeb> locationWebContracts = addTitleLineageToLocation(locationProjections.getContent());
        return new PageImpl<>(locationWebContracts, locationProjections.getPageable(), locationProjections.getTotalElements());
    }

    // This method uses in memory approach instead of database, because for smaller number of addresses the query plan to achive this is expensive due to over estimation by postgres.
    public Map<Long, String> getTitleLineages(List<Long> addressIds) {
        List<AddressLevel> addresses = locationRepository.findAllByIdIn(addressIds);

        HashSet<Long> uniqueAddresses = new HashSet<>();
        addresses.forEach(addressLevel -> uniqueAddresses.addAll(addressLevel.getLineageAddressIds()));
        List<AddressLevel> allAddressesInScope = locationRepository.findAllByIdIn(new ArrayList<>(uniqueAddresses));

        HashMap<Long, String> titleLineages = new HashMap<>();
        addressIds.forEach(addressId -> {
            final AddressLevel addressLevel = addresses.stream().filter(al -> al.getId().equals(addressId)).findFirst().orElseThrow(() -> new AssertionError("Address not found"));

            String lineage = allAddressesInScope.stream()
                    .filter(inScopeAddress -> addressLevel.getLineageAddressIds().contains(inScopeAddress.getId()))
                    .sorted((o1, o2) -> o2.getLevel().compareTo(o1.getLevel()))
                    .map(AddressLevel::getTitle)
                    .collect(Collectors.joining(", "));

            titleLineages.put(addressId, lineage);
        });
        return titleLineages;
    }

    public Optional<AddressLevel> findByAddressMap(Map<String, String> addressMap) {
        if (addressMap == null || addressMap.isEmpty()) {
            return Optional.empty();
        }
        List<AddressLevel> addressLevels = addressMap.entrySet().stream().map(entry -> locationRepository.findLocation(entry.getValue(), entry.getKey()))
                .filter(Objects::nonNull).sorted(Comparator.comparing(al -> al.getLineage().length()))
                .collect(Collectors.toList());

        if (addressLevels.isEmpty() || addressLevels.size() != addressMap.size()) {
            return Optional.empty();
        }

        String typeHierarchyForAddressMap = addressLevels.stream().map(al -> String.valueOf(al.getTypeId())).collect(Collectors.joining("."));
        String lineageForAddressMap = addressLevels.stream().map(al -> String.valueOf(al.getId())).collect(Collectors.joining("."));

        TreeSet<String> addressLevelTypeHierarchies = locationHierarchyService.fetchAndFilterHierarchies();
        if (addressLevelTypeHierarchies.stream().anyMatch(hierarchy -> hierarchy.contains(typeHierarchyForAddressMap))) {
            AddressLevel matchedAddressLevel = addressLevels.get(addressLevels.size() - 1);
            if (matchedAddressLevel.getLineage().equals(lineageForAddressMap)) {
                return Optional.of(matchedAddressLevel);
            }
        }
        return Optional.empty();
    }
}

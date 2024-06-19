package org.avni.server.service;

import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementType;
import org.avni.server.application.KeyType;
import org.avni.server.application.projections.LocationProjection;
import org.avni.server.builder.BuilderException;
import org.avni.server.builder.LocationBuilder;
import org.avni.server.dao.*;
import org.avni.server.dao.sync.SyncEntityName;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.S;
import org.avni.server.util.ValidationUtil;
import org.avni.server.web.request.AddressLevelTypeContract;
import org.avni.server.web.request.LocationContract;
import org.avni.server.web.request.LocationEditContract;
import org.avni.server.web.request.ReferenceDataContract;
import org.avni.server.web.request.webapp.search.LocationSearchRequest;
import org.avni.server.web.validation.ValidationException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class LocationService implements ScopeAwareService<AddressLevel> {

    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final OrganisationRepository organisationRepository;
    private final LocationRepository locationRepository;
    private final LocationMappingRepository locationMappingRepository;
    private final ResetSyncService resetSyncService;
    private final Logger logger;

    @Autowired
    public LocationService(LocationRepository locationRepository, AddressLevelTypeRepository addressLevelTypeRepository, OrganisationRepository organisationRepository, LocationMappingRepository locationMappingRepository, ResetSyncService resetSyncService) {
        this.locationRepository = locationRepository;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.organisationRepository = organisationRepository;
        this.locationMappingRepository = locationMappingRepository;
        this.resetSyncService = resetSyncService;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    public List<AddressLevel> saveAll(List<LocationContract> locationContracts) throws BuilderException {
        List<AddressLevel> saved = new ArrayList<>();
        for (LocationContract contract : locationContracts) saved.add(save(contract));
        return saved;
    }

    public AddressLevel save(LocationContract locationContract) throws BuilderException {
        logger.info(String.format("Processing location request: %s", locationContract.toString()));
        AddressLevelType type = getTypeByUuidOrName(locationContract);
        if (type == null) {
            type = createType(locationContract);
        }
        return saveLocation(locationContract, type);
    }

    private void setParent(LocationContract locationContract, AddressLevelType addressLevelType) {
        Long orgId = UserContextHolder.getUserContext().getOrganisation().getId();
        ReferenceDataContract parentLocationContract = locationContract.getParent();
        if (parentLocationContract != null) {
            AddressLevel parentLocation = locationRepository.findByUuid(parentLocationContract.getUuid());
            AddressLevelType parentAddressLevelType = addressLevelTypeRepository.findByNameIgnoreCaseAndOrganisationIdAndIsVoidedFalse(parentLocation.getType().getName(), orgId);
            addressLevelType.setParent(parentAddressLevelType);
        }
    }

    private AddressLevelType getTypeByUuidOrName(LocationContract locationContract) {
        Long orgId = UserContextHolder.getUserContext().getOrganisation().getId();
        return locationContract.getAddressLevelTypeUUID() != null
                ? addressLevelTypeRepository.findByUuid(locationContract.getAddressLevelTypeUUID())
                : addressLevelTypeRepository.findByNameIgnoreCaseAndOrganisationIdAndIsVoidedFalse(locationContract.getType(), orgId);
    }

    private AddressLevelType createType(LocationContract locationContract) {
        AddressLevelType addressLevelType = new AddressLevelType();
        addressLevelType.setUuid(UUID.randomUUID().toString());
        addressLevelType.setName(locationContract.getType());
        addressLevelType.setLevel(locationContract.getLevel());
        setParent(locationContract, addressLevelType);
        addressLevelTypeRepository.save(addressLevelType);
        return addressLevelType;
    }

    private AddressLevel saveLocation(LocationContract contract, AddressLevelType type) throws BuilderException {
        LocationBuilder locationBuilder = new LocationBuilder(locationRepository.findByUuid(contract.getUuid()), type);
        locationBuilder.copy(contract);
        AddressLevel location = locationBuilder.build();
        updateOrganisationIfNeeded(location, contract);

        // Validate location title/name is unique only if new AddressLevel
        if (location.getId() == null && !titleIsValid(location, contract.getName().trim(), type))
            throw new BuilderException(String.format("Location with same name '%s' and type '%s' exists at this level", contract.getName(), type.getName()));

        try {
            locationRepository.save(location);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new BuilderException(String.format("Unable to create Location{name='%s',level='%s',orgUUID='%s',..}: '%s'", contract.getName(), contract.getLevel(), contract.getOrganisationUUID(), e.getMessage()));
        }
        try {
            location.calculateLineage();
            locationRepository.save(location);
        } catch (Exception e) {
            logger.error(e.getMessage());
            throw new BuilderException(String.format("Unable to update lineage for location with Id %s - %s", location.getId(), e.getMessage()));
        }
        return location;
    }

    private void updateOrganisationIfNeeded(AddressLevel location, @NotNull LocationContract contract) {
        String organisationUuid = contract.getOrganisationUUID();
        if (organisationUuid != null) {
            Organisation organisation = organisationRepository.findByUuid(organisationUuid);
            if (organisation == null) {
                throw new RuntimeException(String.format("Organisation not found with uuid :'%s'", organisationUuid));
            }
            location.setOrganisationId(organisation.getId());
        }
    }

    public AddressLevel update(LocationEditContract locationEditContract, Long id) {
        AddressLevel location;
        if (locationEditContract.getUuid() != null) {
            location = locationRepository.findByUuid(locationEditContract.getUuid());
        } else {
            if (!id.equals(locationEditContract.getId())) {
                String message = String.format("Invalid location id '%d'", locationEditContract.getId());
                throw new RuntimeException(message);
            }
            location = locationRepository.findOne(id);
        }
        if (location == null) {
            String message = String.format("Location with id '%d' uuid '%s' not found", locationEditContract.getId(), locationEditContract.getUuid());
            throw new RuntimeException(message);
        }
        if (locationEditContract.getTitle().trim().equals("")) {
            throw new RuntimeException("Empty 'title' received");
        }

        if (location.isTopLevel() && locationRepository.findByTitleIgnoreCaseAndTypeAndParentIsNull(locationEditContract.getTitle().trim(), location.getType()) != null) {
            throw new RuntimeException("Location with same name '%s' and type '%s' already exists");
        } else {
            AddressLevel parent = locationEditContract.getParentId() == null ? null : locationRepository.findOne(locationEditContract.getParentId());
            if (!titleIsValid(location, parent, locationEditContract.getTitle().trim(), location.getType())) {
                String message = String.format("Location with same name '%s' and type '%s' and parent '%s' already exists",
                        locationEditContract.getTitle(),
                        location.getType().getName(),
                        parent == null ? null : parent.getTitle());
                throw new RuntimeException(message);
            }
        }

        if (locationEditContract.getParentId() != null && !locationEditContract.getParentId().equals(location.getParentId())) {
            updateParent(location, locationEditContract.getParentId());
        }

        location.setTitle(locationEditContract.getTitle());
        locationRepository.save(location);
        return location;
    }

    public void updateParent(AddressLevel location, AddressLevel newParent) {
        updateDescendantLocationLineage(locationRepository.findAllByParent(location), location.getParentId(), newParent.getId());
        updateLocationMapping(location, newParent);
        location.setLineage(updateLineage(location.getLineage(), location.getParentId(), newParent.getId()));
        Long oldParentId = location.getParentId();
        location.setParent(newParent);
        resetSyncService.recordLocationParentChange(location, oldParentId);
    }

    private void updateParent(AddressLevel location, Long newParentId) {
        updateParent(location, locationRepository.findOne(newParentId));
    }

    private void updateLocationMapping(AddressLevel location, AddressLevel newParent) {
        List<ParentLocationMapping> locationMappings = locationMappingRepository.findAllByLocation(location);
        List<ParentLocationMapping> updatedLocationMappings = locationMappings.stream()
                .peek(locationMapping -> locationMapping.setParentLocation(newParent))
                .collect(Collectors.toList());
        locationMappingRepository.saveAll(updatedLocationMappings);
    }

    private String updateLineage(String lineage, Long oldParentId, Long newParentId) {
        if (oldParentId == null) {
            return newParentId + "." + lineage;
        }
        return lineage.replaceAll(oldParentId.toString(), newParentId.toString());
    }

    private void updateDescendantLocationLineage(List<AddressLevel> children, Long oldId, Long newId) {
        if (children.isEmpty()) {
            return;
        } else {
            children.forEach(child -> {
                String lineage = child.getLineage();
                child.setLineage(updateLineage(lineage, oldId, newId));
                locationRepository.save(child);
                updateDescendantLocationLineage(locationRepository.findAllByParent(child), oldId, newId);
            });
        }
    }

    private boolean titleIsValid(AddressLevel location, String title, AddressLevelType type) {
        return (location.isTopLevel() && locationRepository.findByTitleIgnoreCaseAndTypeAndParentIsNull(title, type) == null)
                || (!location.isTopLevel() && !location.getParent().containsSubLocation(title, type));
    }

    private boolean titleIsValid(AddressLevel location, AddressLevel parent, String title, AddressLevelType type) {
        return (location.isTopLevel() && locationRepository.findByTitleIgnoreCaseAndTypeAndParentIsNull(title, type) == null)
                || (!location.isTopLevel() && parent != null && !parent.containsSubLocationExcept(title, type, location));
    }

    public AddressLevelType createAddressLevelType(AddressLevelTypeContract contract) {
        AddressLevelType addressLevelType = addressLevelTypeRepository.findByUuid(contract.getUuid());
        if (addressLevelType == null) {
            addressLevelType = new AddressLevelType();
            addressLevelType.setUuid(contract.getUuid());
        }
        if (contract.getUuid() == null)
            addressLevelType.setUuid(UUID.randomUUID().toString());
        if (ValidationUtil.checkNullOrEmptyOrContainsDisallowedCharacters(contract.getName(), ValidationUtil.COMMON_INVALID_CHARS_PATTERN)) {
            throw new ValidationException(String.format("Invalid Location Type name %s", contract.getName()));
        }
        addressLevelType.setName(contract.getName());
        if (ValidationUtil.checkNull(contract.getLevel())) {
            throw new ValidationException(String.format("Invalid Location Type level %s", contract.getLevel()));
        }
        addressLevelType.setLevel(contract.getLevel());
        addressLevelType.setVoided(contract.isVoided());
        AddressLevelType parent = null;
        if (contract.getParent() != null && StringUtils.hasText(contract.getParent().getName())) {
            parent = addressLevelTypeRepository.findByName(contract.getParent().getName());
        } else if (contract.getParent() != null) {
            parent = addressLevelTypeRepository.findByUuid(contract.getParent().getUuid());
        }
        if (contract.getParentId() != null) {
            parent = addressLevelTypeRepository.findOne(contract.getParentId());
        }
        addressLevelType.setParent(parent);
        addressLevelTypeRepository.save(addressLevelType);
        return addressLevelType;
    }

    public List<Long> getAllWithChildrenForUUIDs(List<String> locationUUIDs) {
        List<Long> allAddressLevels = new ArrayList<>();
        if (locationUUIDs == null) return allAddressLevels;
        locationRepository.findByUuidIn(locationUUIDs).forEach(addressLevel -> {
            String lquery = "*.".concat(addressLevel.getLineage()).concat(".*");
            List<Long> allChildrenLocations = locationRepository.getAllChildrenLocationsIds(lquery);
            allAddressLevels.addAll(allChildrenLocations);
        });
        return allAddressLevels;
    }

    @Override
    public boolean isScopeEntityChanged(DateTime lastModifiedDateTime, String typeUUID) {
        User user = UserContextHolder.getUserContext().getUser();
        return isChangedByCatchment(user, lastModifiedDateTime, SyncEntityName.Location);
    }

    @Override
    public OperatingIndividualScopeAwareRepository<AddressLevel> repository() {
        return locationRepository;
    }

    public Object getObservationValueForUpload(FormElement formElement, String answerValue) {
        Concept concept = formElement.getConcept();
        List<String> lowestLevelUuids = (List<String>) concept.getKeyValues().get(KeyType.lowestAddressLevelTypeUUIDs).getValue();
        List<AddressLevelType> lowestLevels = lowestLevelUuids.stream()
                .map(addressLevelTypeRepository::findByUuid)
                .collect(Collectors.toList());
        if (formElement.getType().equals(FormElementType.MultiSelect.name())) {
            String[] providedAnswers = S.splitMultiSelectAnswer(answerValue);
            return Stream.of(providedAnswers)
                    .map(answer -> locationRepository.findByTitleIgnoreCaseAndTypeIn(answer, lowestLevels).getUuid())
                    .collect(Collectors.toList());
        } else {
            return locationRepository.findByTitleIgnoreCaseAndTypeIn(answerValue, lowestLevels).getUuid();
        }
    }

    public Page<LocationProjection> find(LocationSearchRequest searchRequest) {
        if (searchRequest.getAddressLevelTypeId() == null) {
            return locationRepository.findLocationProjectionByTitleIgnoreCase(searchRequest.getTitle(), searchRequest.getPageable());
        }
        if (searchRequest.getParentId() == null) {
            return locationRepository.findLocationProjectionByTitleIgnoreCaseAndTypeId(searchRequest.getTitle(), searchRequest.getAddressLevelTypeId(), searchRequest.getPageable());
        }
        return locationRepository.findLocationProjectionByTitleIgnoreCaseAndTypeIdAndParentId(searchRequest.getTitle(), searchRequest.getAddressLevelTypeId(), searchRequest.getParentId(), searchRequest.getPageable());
    }

    public List<LocationProjection> getParents(String uuid, Long maxLevelTypeId) {
        return maxLevelTypeId == null ?
         locationRepository.getParents(uuid) : locationRepository.getParentsWithMaxLevelTypeId(uuid, maxLevelTypeId);
    }
}

package org.avni.server.service;

import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.CHSEntity;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.OrganisationConfig;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.SubjectTypeSetting;
import org.avni.server.framework.security.UserContextHolder;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class LocationHierarchyService implements NonScopeAwareService {

    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final LocationRepository locationRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final Logger logger;

    private static final Pattern LEGACY_ID_SEGMENT = Pattern.compile("^\\d+$");
    private static final Pattern UUID_SEGMENT = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    @Autowired
    public LocationHierarchyService(OrganisationConfigService organisationConfigService, AddressLevelTypeRepository addressLevelTypeRepository, LocationRepository locationRepository, SubjectTypeRepository subjectTypeRepository) {
        this.organisationConfigService = organisationConfigService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.locationRepository = locationRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    public List<Long> getLowestAddressLevelTypeHierarchiesForOrganisation() {
        OrganisationConfig organisationConfig = organisationConfigService.getOrganisationConfig(UserContextHolder.getUserContext().getOrganisation());
        ArrayList<String> lowestLevelAddressLevelTypeHierarchies = (ArrayList<String>) organisationConfig.getConfigValue(OrganisationConfigSettingKey.lowestAddressLevelType);
        if (lowestLevelAddressLevelTypeHierarchies == null) {
            return null;
        }

        LinkedHashSet<String> segments = new LinkedHashSet<>();
        for (String hierarchy : lowestLevelAddressLevelTypeHierarchies) {
            segments.addAll(Arrays.asList(hierarchy.split(Pattern.quote("."))));
        }

        LinkedHashSet<Long> addressLevelTypeIds = new LinkedHashSet<>();
        Set<String> uuidSegments = new HashSet<>();
        for (String segment : segments) {
            if (LEGACY_ID_SEGMENT.matcher(segment).matches()) {
                // Legacy ID lineage (pre-#871). Dual-read fallback retained for several releases; remove once all orgs are migrated.
                addressLevelTypeIds.add(Long.valueOf(segment));
            } else {
                uuidSegments.add(segment);
            }
        }
        if (!uuidSegments.isEmpty()) {
            addressLevelTypeRepository.findByUuidIn(uuidSegments)
                    .forEach(addressLevelType -> addressLevelTypeIds.add(addressLevelType.getId()));
        }
        return new ArrayList<>(addressLevelTypeIds);
    }

    public TreeSet<String> determineAddressHierarchiesToBeSaved(JsonObject organisationSettings, HashSet<String> locationConceptUuids) {
        List<AddressLevelType> addressLevelTypes = addressLevelTypeRepository.findByUuidIn(locationConceptUuids);

        TreeSet<String> addressLevelTypeHierarchies = buildHierarchyForAddressLevelTypes(addressLevelTypes);

        if (organisationSettings.containsKey(String.valueOf(OrganisationConfigSettingKey.lowestAddressLevelType))) {
            ArrayList<String> currentAddressLevelTypeHierarchies = (ArrayList<String>) organisationSettings.get(String.valueOf(OrganisationConfigSettingKey.lowestAddressLevelType));
            if (currentAddressLevelTypeHierarchies != null)
                addressLevelTypeHierarchies.addAll(currentAddressLevelTypeHierarchies);
        }

        // Keep only UUID-shaped lineages so legacy ID lineages are never re-persisted once migrated (#871).
        addressLevelTypeHierarchies.removeIf(lineage -> !isUuidLineage(lineage));

        return filterHierarchiesWithCommonAncestries(addressLevelTypeHierarchies);
    }

    public Map<String, String> determineAddressHierarchiesForAllAddressLevelTypesInOrg() {
        HashMap<String, String> hierarchyToDisplayNameMap = new HashMap<>();
        TreeSet<String> filteredAddressLevelTypeHierarchies = fetchAndFilterHierarchies();
        filteredAddressLevelTypeHierarchies.forEach(key -> {
            hierarchyToDisplayNameMap.put(key, Arrays.stream(key.split("\\."))
                    .map(altUuid -> addressLevelTypeRepository.findByUuid(altUuid).getName())
                    .collect(Collectors.joining(" -> ")));
        });
        return hierarchyToDisplayNameMap;
    }

    public TreeSet<String> fetchAndFilterHierarchies() {
        List<AddressLevelType> addressLevelTypes = addressLevelTypeRepository.findAllByIsVoidedFalse();
        TreeSet<String> addressLevelTypeHierarchies = buildHierarchyForAddressLevelTypes(addressLevelTypes);
        return filterHierarchiesWithCommonAncestries(addressLevelTypeHierarchies);
    }

    public TreeSet<String> buildHierarchyForAddressLevelTypes(List<AddressLevelType> addressLevelTypes) {
        TreeSet<String> addressLevelTypeHierarchies = new TreeSet<>();

        addressLevelTypes.forEach(addressLevelType -> {
            StringBuilder hierarchy = new StringBuilder(addressLevelType.getUuid());
            Optional<AddressLevelType> parent = Optional.ofNullable(addressLevelType.getParent());
            while (parent.isPresent()) {
                hierarchy.insert(0, parent.get().getUuid() + ".");
                parent = (parent.get().getParent() != null) ?
                        addressLevelTypeRepository.findById(parent.get().getParentId())
                        :
                        Optional.empty();
            }
            addressLevelTypeHierarchies.add(hierarchy.toString());
        });

        return addressLevelTypeHierarchies;
    }

    public TreeSet<String> filterHierarchiesWithCommonAncestries(TreeSet<String> addressLevelTypeHierarchies) {
        if (addressLevelTypeHierarchies.size() > 1) {
            TreeSet<String> dolly = new TreeSet<>(addressLevelTypeHierarchies); //cloning in order to iterate and compare the next element with the current one
            Iterator<String> oneStepAheadIterator = dolly.iterator();
            oneStepAheadIterator.next();
            Iterator<String> addressLevelTypeHierarchiesIterator = addressLevelTypeHierarchies.iterator();
            addressLevelTypeHierarchiesIterator.forEachRemaining(key -> {
                if (oneStepAheadIterator.hasNext() && oneStepAheadIterator.next().startsWith(key)) {
                    addressLevelTypeHierarchiesIterator.remove();
                }
            });
        }
        return addressLevelTypeHierarchies;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        ArrayList<Long> addressLevelTypeIds = (ArrayList<Long>) this.getLowestAddressLevelTypeHierarchiesForOrganisation();
        if (addressLevelTypeIds != null) {
            List<AddressLevelType> addressLevelTypes = addressLevelTypeRepository.findAllByIdIn(addressLevelTypeIds);
            return locationRepository.existsByLastModifiedDateTimeAfterAndTypeIn(CHSEntity.toDate(lastModifiedDateTime), addressLevelTypes);
        }
        return false;
    }

    public Map<String, Object> determineAddressHierarchiesForAllSubjectTypes() {
        Map<String, Object> result = new HashMap<>();
        List<SubjectType> allSubjectTypes = subjectTypeRepository.findAllByIsVoidedFalse();

        allSubjectTypes.forEach(subjectType -> {
            Map<String, String> availableHierarchies = getAvailableHierarchiesForSubjectType(subjectType);
            
            Map<String, Object> subjectInfo = new HashMap<>();
            subjectInfo.put("availableHierarchies", availableHierarchies);
            
            result.put(subjectType.getName(), subjectInfo);
        });

        return result;
    }

    public Map<String, String> getAvailableHierarchiesForSubjectType(SubjectType subjectType) {
        List<SubjectTypeSetting> customRegistrationLocations = organisationConfigService.getCurrentOrganisationConfig().getCustomRegistrationLocations();
        Optional<SubjectTypeSetting> setting = customRegistrationLocations.stream()
                .filter(s -> s.getSubjectTypeUUID().equals(subjectType.getUuid()))
                .findFirst();
        
        if (setting.isEmpty()) {
            return determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        }
        
        List<String> locationTypeUUIDs = setting.get().getLocationTypeUUIDs();
        if (locationTypeUUIDs == null || locationTypeUUIDs.isEmpty()) {
            return determineAddressHierarchiesForAllAddressLevelTypesInOrg();
        }
        
        List<AddressLevelType> lowestLocationTypes = addressLevelTypeRepository.findByUuidIn(locationTypeUUIDs)
                .stream()
                .filter(alt -> !alt.isVoided())
                .collect(Collectors.toList());

        TreeSet<String> hierarchyKeys = buildHierarchyForAddressLevelTypes(lowestLocationTypes);
        Map<String, String> result = new HashMap<>();
        for (String key : hierarchyKeys) {
            String displayName = Arrays.stream(key.split("\\."))
                    .map(uuid -> addressLevelTypeRepository.findByUuid(uuid).getName())
                    .collect(Collectors.joining(" -> "));
            result.put(key, displayName);
        }
        return result;
    }

    private boolean isUuidLineage(String lineage) {
        return Arrays.stream(lineage.split(Pattern.quote(".")))
                .allMatch(segment -> UUID_SEGMENT.matcher(segment).matches());
    }
}

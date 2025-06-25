package org.avni.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.avni.server.application.KeyType;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.OrganisationConfigRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.projection.ConceptProjection;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.ReactAdminUtil;
import org.avni.server.web.request.OrganisationConfigRequest;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class OrganisationConfigService implements NonScopeAwareService {
    public static final String EMPTY_STRING = "";
    public static final String UUID = "uuid";

    private static final Logger logger = LoggerFactory.getLogger(OrganisationConfigService.class);

    private final OrganisationConfigRepository organisationConfigRepository;
    private final ProjectionFactory projectionFactory;
    private final ConceptRepository conceptRepository;
    private final LocationHierarchyService locationHierarchyService;
    private final ObjectMapper objectMapper;

    @Autowired
    public OrganisationConfigService(OrganisationConfigRepository organisationConfigRepository,
                                     ProjectionFactory projectionFactory,
                                     ConceptRepository conceptRepository,
                                     @Lazy LocationHierarchyService locationHierarchyService) {
        this.organisationConfigRepository = organisationConfigRepository;
        this.projectionFactory = projectionFactory;
        this.conceptRepository = conceptRepository;
        this.locationHierarchyService = locationHierarchyService;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    @Transactional
    public OrganisationConfig saveOrganisationConfig(OrganisationConfigRequest request, Organisation organisation) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisation.getId());
        if (organisationConfig == null) {
            organisationConfig = new OrganisationConfig();
        }
        organisationConfig.setOrganisationId(organisation.getId());
        organisationConfig.setUuid(request.getUuid() == null ? java.util.UUID.randomUUID().toString() : request.getUuid());
        organisationConfig.setSettings(request.getSettings());
        organisationConfig.setWorklistUpdationRule(request.getWorklistUpdationRule());
        organisationConfig.updateLastModifiedDateTime();
        organisationConfigRepository.save(organisationConfig);
        return organisationConfig;
    }

    public OrganisationConfig createDefaultOrganisationConfig(Organisation organisation) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisation.getId());
        if (Objects.isNull(organisationConfig)) {
            organisationConfig = new OrganisationConfig();
            organisationConfig.assignUUID();
            organisationConfig.setOrganisationId(organisation.getId());
        }
        Map<String, Object> settings = new HashMap<>();
        settings.put("languages", new String[]{"en"});
        JsonObject jsonObject = new JsonObject(settings);
        organisationConfig.setSettings(jsonObject);
        organisationConfig.setExportSettings(new JsonObject());
        organisationConfig.setWorklistUpdationRule(null);
        return organisationConfigRepository.save(organisationConfig);
    }

    public OrganisationConfig getOrganisationConfig(Organisation organisation) {
        return organisationConfigRepository.findByOrganisationId(organisation.getId());
    }

    public OrganisationConfig getOrganisationConfigByOrgId(Long organisationId) {
        return organisationConfigRepository.findByOrganisationId(organisationId);
    }

    public LinkedHashMap<String, Object> getOrganisationSettings(Long organisationId) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationId);
        JsonObject settings = new JsonObject(organisationConfig.getSettings());
        LinkedHashMap<String, Object> organisationSettingsConceptListMap = new LinkedHashMap<>();
        List<String> conceptUuidList = new ArrayList<>();
        JsonObject searchFilters = new JsonObject().with("searchFilters", settings.getOrDefault("searchFilters", Collections.emptyList()));
        try {
            JSONObject jsonObj = new JSONObject(searchFilters.toString());
            JSONArray jsonArray = jsonObj.getJSONArray("searchFilters");
            for (int i = 0; i < jsonArray.length(); i++) {
                if (jsonArray.getJSONObject(i).has("conceptUUID")) {
                    String uuid = jsonArray.getJSONObject(i).getString("conceptUUID");
                    if (null != uuid && !EMPTY_STRING.equals(uuid.trim()))
                        conceptUuidList.add(uuid.trim());
                }
            }
        } catch (JSONException e) {
            logger.error("Ignoring JSONException " + e.getMessage() + " and setting empty searchFilters array in response.");
            settings.put("searchFilters", Collections.emptyList());
        }
        List<ConceptProjection> conceptList = conceptRepository.getAllConceptByUuidIn(conceptUuidList).stream()
                .map(concept -> projectionFactory.createProjection(ConceptProjection.class, concept))
                .collect(Collectors.toList());
        organisationSettingsConceptListMap.put("organisationConfig", settings);
        organisationSettingsConceptListMap.put("conceptList", conceptList);
        return organisationSettingsConceptListMap;
    }

    public JsonObject getOrganisationSettingsJson(Long organisationId) {
        return organisationConfigRepository.findByOrganisationId(organisationId).getSettings();
    }

    public Optional<Object> getOrganisationSettingsValue(Organisation organisation, OrganisationConfigSettingKey settingKey) {
        JsonObject jsonObject = this.getOrganisationSettingsJson(organisation.getId());
        return Optional.ofNullable(jsonObject.get(settingKey.name()));
    }

    @Transactional
    public OrganisationConfig updateLowestAddressLevelTypeSetting(HashSet<String> locationConceptUuids) {
        try {
            JsonObject organisationSettings = getOrganisationSettingsJson(UserContextHolder.getUserContext().getOrganisationId());

            JsonObject settings = new JsonObject();
            settings.put(String.valueOf(OrganisationConfigSettingKey.lowestAddressLevelType), locationHierarchyService.determineAddressHierarchiesToBeSaved(organisationSettings, locationConceptUuids));
            return updateOrganisationSettings(settings);
        } catch (Exception exception) {
            logger.error("Error updating lowest address level type setting", exception);
        }
        return null;
    }

    @Transactional
    public void updateSettings(String key, Object settingObject) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findAll()
                .stream().findFirst()
                .orElse(new OrganisationConfig());
        JsonObject jsonObject = organisationConfig.getSettings();
        jsonObject.with(key, settingObject);
        organisationConfig.updateLastModifiedDateTime();
        organisationConfigRepository.save(organisationConfig);
    }

    @Transactional
    public OrganisationConfig updateOrganisationSettings(JsonObject settings) {
        long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationId);
        if (organisationConfig == null) {
            organisationConfig = new OrganisationConfig();
        }
        organisationConfig.setOrganisationId(organisationId);
        organisationConfig.setUuid(java.util.UUID.randomUUID().toString());
        organisationConfig.updateLastModifiedDateTime();

        organisationConfig.setSettings(updateOrganisationConfigSettings(settings, organisationConfig.getSettings()));

        return organisationConfigRepository.save(organisationConfig);
    }

    public JsonObject updateOrganisationConfigSettings(JsonObject newSettings, JsonObject currentSettings) {
        newSettings.keySet().forEach(key -> {
            currentSettings.put(key, newSettings.get(key));
        });
        return currentSettings;
    }

    @Transactional
    public OrganisationConfig updateOrganisationConfig(OrganisationConfigRequest request, OrganisationConfig organisationConfig) {

        if (request.getWorklistUpdationRule() != null)
            organisationConfig.setWorklistUpdationRule(request.getWorklistUpdationRule());
        if (request.getSettings() != null)
            organisationConfig.setSettings(updateOrganisationConfigSettings(request.getSettings(), organisationConfig.getSettings()));
        organisationConfig.updateAudit();
        return organisationConfigRepository.save(organisationConfig);
    }

    public Object getSettingsByKey(String key) {
        OrganisationConfig currentOrganisationConfig = this.getCurrentOrganisationConfig();
        return currentOrganisationConfig.getSettings().getOrDefault(key, Collections.EMPTY_LIST);
    }

    public OrganisationConfig getCurrentOrganisationConfig() {
        Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        return organisationConfigRepository.findByOrganisationId(organisationId);
    }

    public void saveRegistrationLocation(AddressLevelType addressLevelType, SubjectType subjectType) {
        this.saveRegistrationLocations(Collections.singletonList(addressLevelType.getUuid()), subjectType);
    }

    public void saveRegistrationLocations(List<String> locationTypeUUIDs, SubjectType subjectType) {
        Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationId);
        String settingsKeyName = KeyType.customRegistrationLocations.toString();
        List<SubjectTypeSetting> updatedCustomRegistrationLocations = getUpdatedCustomRegistrationLocations(locationTypeUUIDs, subjectType, organisationConfig);
        organisationConfig.getSettings().put(settingsKeyName, updatedCustomRegistrationLocations);
        organisationConfigRepository.save(organisationConfig);
    }

    private List<SubjectTypeSetting> getUpdatedCustomRegistrationLocations(List<String> locationTypeUUIDs, SubjectType subjectType, OrganisationConfig organisationConfig) {
        List<SubjectTypeSetting> savedSettings = organisationConfig.getCustomRegistrationLocations();
        List<SubjectTypeSetting> otherSubjectTypeSettings = filterSubjectTypeSettingsBasedOn(savedSettings, setting -> !setting.getSubjectTypeUUID().equals(subjectType.getUuid()));
        SubjectTypeSetting subjectTypeSetting = new SubjectTypeSetting();
        subjectTypeSetting.setSubjectTypeUUID(subjectType.getUuid());
        subjectTypeSetting.setLocationTypeUUIDs(locationTypeUUIDs);
        otherSubjectTypeSettings.add(subjectTypeSetting);
        return filterSubjectTypeSettingsBasedOn(otherSubjectTypeSettings, setting -> setting.getLocationTypeUUIDs() != null);
    }

    private List<SubjectTypeSetting> filterSubjectTypeSettingsBasedOn(List<SubjectTypeSetting> subjectTypeSettings, Predicate<SubjectTypeSetting> predicate) {
        return subjectTypeSettings
                .stream()
                .filter(predicate)
                .collect(Collectors.toList());
    }

    private Boolean isFeatureEnabled(String feature) {
        Long organisationId = UserContextHolder.getUserContext().getOrganisationId();
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisationId);
        if (organisationConfig == null) {
            return false;
        }
        return organisationConfig.isFeatureEnabled(feature);
    }

    public List<OrganisationConfig> findAllWithFeatureEnabled(String feature) {
        return organisationConfigRepository.findAll()
                .stream()
                .filter(organisationConfig -> organisationConfig.isFeatureEnabled(feature))
                .collect(Collectors.toList());
    }

    public boolean isCommentEnabled() {
        return isFeatureEnabled("enableComments");
    }

    public boolean isMessagingEnabled() {
        return isFeatureEnabled(OrganisationConfigSettingKey.enableMessaging.name());
    }

    public boolean isFailOnValidationErrorEnabled() {
        return isFeatureEnabled(OrganisationConfigSettingKey.failOnValidationError.name());
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return organisationConfigRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public JsonObject getExportSettings() {
        Optional<OrganisationConfig> organisationConfig = getOrganisationConfig();
        if (organisationConfig.isPresent()) {
            return organisationConfig.get().getExportSettings();
        }
        return new JsonObject();
    }

    public ResponseEntity<?> saveNewExportSettings(String name, JsonObject request) {
        return saveExportSettings(name, request, false);
    }

    public ResponseEntity<?> updateExistingExportSettings(String name, JsonObject request) {
        return saveExportSettings(name, request, true);
    }

    private ResponseEntity<?> saveExportSettings(String name, JsonObject request, boolean shouldExist) {
            OrganisationConfig organisationConfig = getOrganisationConfig().orElse(new OrganisationConfig());
        if (StringUtils.hasText(name)) {
            JsonObject exportSettings = getExportSettings(organisationConfig);
            JsonObject exportSettingsForName = (JsonObject) exportSettings.get(name);
            if(shouldExist && exportSettingsForName == null) {
                return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("ExportSettings for name %s is not available to update", name)));
            } else if(!shouldExist && exportSettingsForName != null) {
                return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("ExportSettings for name %s already exists ", name)));
            }
            exportSettings.put(name, request);
            organisationConfig.assignUUIDIfRequired();
            organisationConfigRepository.save(organisationConfig);
            return ResponseEntity.ok(request);
        }
        return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("ExportSettings name is not specified in the save request"));
    }

    private Optional<OrganisationConfig> getOrganisationConfig() {
        return organisationConfigRepository.findAllByIsVoidedFalse().stream().findFirst();
    }

    private JsonObject getExportSettings(OrganisationConfig organisationConfig) {
        JsonObject savedSettings = organisationConfig.getExportSettings();
        if (savedSettings == null) {
            savedSettings = new JsonObject();
            organisationConfig.setSettings(savedSettings);
        }
        return savedSettings;
    }

    public ResponseEntity<?> deleteExportSettings(String name) {
        if (!StringUtils.hasText(name)) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError("ExportSettings name is not specified in the save request"));
        }
        OrganisationConfig organisationConfig = getOrganisationConfig().orElse(new OrganisationConfig());
        JsonObject savedSettings = getExportSettings(organisationConfig);
        if (savedSettings == null || savedSettings.get(name) == null) {
            return ResponseEntity.badRequest().body(ReactAdminUtil.generateJsonError(String.format("ExportSettings for specified name \'%s\' not found", name)));
        }
        savedSettings.remove(name);
        organisationConfigRepository.save(organisationConfig);
        return ResponseEntity.ok().build();
    }

    public OrganisationConfig deleteMetadataRelatedSettings() {
        OrganisationConfig organisationConfig = getCurrentOrganisationConfig();
        organisationConfig.setSettings(updateOrganisationConfigSettings(getMetadataConfigSettingsAfterReset(),
                organisationConfig.getSettings()));
        organisationConfig.setExportSettings(new JsonObject());
        organisationConfig.setWorklistUpdationRule(null);
        organisationConfig.updateAudit();
        return organisationConfigRepository.save(organisationConfig);
    }

    private JsonObject getMetadataConfigSettingsAfterReset() {
        OrganisationConfigSettingKey[] metadataRelatedConfigSettingKeys = {
                OrganisationConfigSettingKey.searchFilters,
                OrganisationConfigSettingKey.customRegistrationLocations,
                OrganisationConfigSettingKey.myDashboardFilters,
                OrganisationConfigSettingKey.searchResultFields,
                OrganisationConfigSettingKey.lowestAddressLevelType
        };
        JsonObject settings = new JsonObject();
        Arrays.asList(metadataRelatedConfigSettingKeys).stream()
                .forEach(settingKey -> settings.put(String.valueOf(settingKey), Collections.emptyList()));
        settings.put(OrganisationConfig.Extension.EXTENSION_DIR, Collections.emptyList());
        return settings;
    }

    public void setMetabaseSetupEnabled(Organisation organisation, boolean isEnabled) {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getId());
        config.setMetabaseSetupEnabled(isEnabled);
        organisationConfigRepository.save(config);
    }

    public boolean isMetabaseSetupEnabled(Organisation organisation) {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getId());
        return config.isMetabaseSetupEnabled();
    }

    public String getMetabaseSyncStatus(Organisation organisation) {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getId());
        return config.getMetabaseSyncStatus();
    }

    public void setMetabaseSyncStatus(Organisation organisation, String status) {
        OrganisationConfig config = organisationConfigRepository.findByOrganisationId(organisation.getId());
        config.setMetabaseSyncStatus(status);
        organisationConfigRepository.save(config);
    }

    public void removeVoidedAddressLevelTypeFromCustomRegistrationLocations(Organisation organisation, String voidedAddressLevelTypeUuid) {
        OrganisationConfig organisationConfig = organisationConfigRepository.findByOrganisationId(organisation.getId());
        String settingsKeyName = KeyType.customRegistrationLocations.toString();

        // Get the current custom registration location settings
        List<SubjectTypeSetting> customRegistrationLocations = organisationConfig.getCustomRegistrationLocations();

        // For each subject type setting, remove the voided address level type from locationTypeUUIDs
        List<SubjectTypeSetting> updatedSettings = new ArrayList<>();
        boolean configChanged = false;

        for (SubjectTypeSetting setting : customRegistrationLocations) {
            List<String> locationTypeUUIDs = setting.getLocationTypeUUIDs();
            if (locationTypeUUIDs != null && locationTypeUUIDs.contains(voidedAddressLevelTypeUuid)) {
                // Remove the voided type from the list
                locationTypeUUIDs.remove(voidedAddressLevelTypeUuid);
                configChanged = true;
            }

            // Only add settings that still have at least one location type
            if (locationTypeUUIDs != null && !locationTypeUUIDs.isEmpty()) {
                updatedSettings.add(setting);
            } else {
                configChanged = true; // Setting removed completely
            }
        }

        // Update the config if changes were made
        if (configChanged) {
            organisationConfig.getSettings().put(settingsKeyName, updatedSettings);
            organisationConfigRepository.save(organisationConfig);
        }
    }
}

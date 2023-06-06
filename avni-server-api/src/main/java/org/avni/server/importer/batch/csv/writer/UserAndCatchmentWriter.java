package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.Locale;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.avni.server.util.S;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.avni.server.domain.OperatingIndividualScope.ByCatchment;

@Component
public class UserAndCatchmentWriter implements ItemWriter<Row>, Serializable {
    private final UserService userService;
    private final CatchmentService catchmentService;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;
    private final OrganisationConfigService organisationConfigService;
    private final IdpServiceFactory idpServiceFactory;
    private final SubjectTypeService subjectTypeService;
    private final ConceptService conceptService;
    private final Pattern compoundHeaderPattern;

    @Autowired
    public UserAndCatchmentWriter(CatchmentService catchmentService,
                                  LocationRepository locationRepository,
                                  UserService userService,
                                  UserRepository userRepository,
                                  OrganisationConfigService organisationConfigService,
                                  IdpServiceFactory idpServiceFactory,
                                  SubjectTypeService subjectTypeService, ConceptService conceptService) {
        this.catchmentService = catchmentService;
        this.locationRepository = locationRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.organisationConfigService = organisationConfigService;
        this.idpServiceFactory = idpServiceFactory;
        this.subjectTypeService = subjectTypeService;
        this.conceptService = conceptService;
        this.compoundHeaderPattern = Pattern.compile("^(?<subjectTypeName>.*?)->(?<conceptName>.*)$");
    }

    @Override
    public void write(List<? extends Row> rows) throws Exception {
        for (Row row : rows) write(row);
    }

    private void write(Row row) throws Exception {
        String fullAddress = row.get("Location with full hierarchy");
        String catchmentName = row.get("Catchment Name");
        String nameOfUser = row.get("Full Name of User");
        String username = row.get("Username");
        String email = row.get("Email");
        String phoneNumber = row.get("Phone");
        String language = row.get("Language");
        Locale locale = S.isEmpty(language) ? Locale.en : Locale.valueByName(language);
        Boolean trackLocation = row.getBool("Track Location");
        String datePickerMode = row.get("Date picker mode");
        Boolean beneficiaryMode = row.getBool("Enable Beneficiary mode");
        String idPrefix = row.get("Beneficiary ID Prefix");

        List<String> syncAttributeHeadersForSubjectTypes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        Map<String, UserSyncSettings> syncSettingsMap = findSyncSettings(row, syncAttributeHeadersForSubjectTypes);
        JsonObject syncSettings = new JsonObject().with(User.SyncSettingKeys.subjectTypeSyncSettings.name(),
                new ArrayList<>(syncSettingsMap.values()));

        AddressLevel location = locationRepository.findByTitleLineageIgnoreCase(fullAddress)
                .orElseThrow(() -> new Exception(format(
                        "Provided Location does not exist. Please check for spelling mistakes '%s'", fullAddress)));

        Catchment catchment = catchmentService.createOrUpdate(catchmentName, location);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        String userSuffix = "@".concat(organisation.getUsernameSuffix());
        User.validateUsername(username, userSuffix);
        User user = userRepository.findByUsername(username);
        User currentUser = userService.getCurrentUser();
        if (user != null) {
            user.setAuditInfo(currentUser);
            userService.save(user);
            return;
        }
        user = new User();
        user.assignUUIDIfRequired();
        user.setUsername(username);
        User.validateEmail(email);
        user.setEmail(email);
        User.validatePhoneNumber(phoneNumber);
        user.setPhoneNumber(phoneNumber);
        user.setName(nameOfUser);
        user.setCatchment(catchment);
        user.setOperatingIndividualScope(ByCatchment);
        user.setSyncSettings(syncSettings);

        user.setSettings(new JsonObject()
                .with("locale", locale)
                .with("trackLocation", trackLocation)
                .withEmptyCheck("datePickerMode", datePickerMode)
                .with("showBeneficiaryMode", beneficiaryMode)
                .withEmptyCheck("idPrefix", idPrefix));

        user.setOrganisationId(organisation.getId());
        user.setAuditInfo(currentUser);
        idpServiceFactory.getIdpService(organisation).createUser(user, organisationConfigService.getOrganisationConfig(organisation));
        userService.save(user);
        userService.addToDefaultUserGroup(user);
    }

    private Map<String, UserSyncSettings> findSyncSettings(Row row, List<String> syncAttributeHeadersForSubjectTypes) {
        Map<String, UserSyncSettings> syncSettingsMap = new HashMap<>();
        syncAttributeHeadersForSubjectTypes.forEach((saHeader) -> {
            Matcher headerPatternMatcher = compoundHeaderPattern.matcher(saHeader);
            if (headerPatternMatcher.matches()) {
                String subjectTypeName = headerPatternMatcher.group("subjectTypeName");
                SubjectType subjectType = subjectTypeService.getByName(subjectTypeName);
                UserSyncSettings userSyncSettings = syncSettingsMap.getOrDefault(subjectType.getUuid(), new UserSyncSettings());
                String subjectTypeUuid = subjectType.getUuid();
                userSyncSettings.setSubjectTypeUUID(subjectTypeUuid);

                String conceptName = headerPatternMatcher.group("conceptName");
                List<String> syncSettingsConceptRawValues = Arrays.asList(row.get(saHeader).split(","));
                updateSyncConceptSettings(syncSettingsConceptRawValues, userSyncSettings, subjectType, conceptName);

                syncSettingsMap.put(subjectType.getUuid(), userSyncSettings);
            }
        });

        return syncSettingsMap;
    }

    private void updateSyncConceptSettings(List<String> syncSettingsValues, UserSyncSettings userSyncSettings, SubjectType subjectType, String conceptName) {
        Concept concept = conceptService.getByName(conceptName);
        String conceptUuid = concept.getUuid();

        List<String> syncSettingsConceptProcessedValues = concept.isCoded() ?
                findSyncSettingCodedConceptValues(syncSettingsValues) : syncSettingsValues;

        String syncRegistrationConcept1 = subjectType.getSyncRegistrationConcept1();
        if (syncRegistrationConcept1.equals(conceptUuid)) {
            userSyncSettings.setSyncConcept1(conceptUuid);
            userSyncSettings.setSyncConcept1Values(syncSettingsConceptProcessedValues);
        } else {
            userSyncSettings.setSyncConcept2(conceptUuid);
            userSyncSettings.setSyncConcept2Values(syncSettingsConceptProcessedValues);
        }
    }

    private List<String> findSyncSettingCodedConceptValues(List<String> syncSettingsValues) {
        List<String> syncSettingCodedConceptValues = new ArrayList<>();
        for (String syncSettingsValue : syncSettingsValues) {
            Optional<String> conceptAnswerUuid = Optional.ofNullable(conceptService.getByName(syncSettingsValue))
                    .map(CHSBaseEntity::getUuid);
            syncSettingCodedConceptValues.add(conceptAnswerUuid.get());
        }

        return syncSettingCodedConceptValues;
    }
}
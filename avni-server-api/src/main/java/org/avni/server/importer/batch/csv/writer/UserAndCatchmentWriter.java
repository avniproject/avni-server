package org.avni.server.importer.batch.csv.writer;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Locale;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.csv.writer.header.UsersAndCatchmentsHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.avni.server.util.RegionUtil;
import org.avni.server.util.S;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.avni.server.web.validation.ValidationException;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.avni.server.domain.OperatingIndividualScope.ByCatchment;
import static org.avni.server.importer.batch.csv.writer.header.UsersAndCatchmentsHeaders.*;

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
    private final ResetSyncService resetSyncService;
    private static final String METADATA_ROW_START_STRING = "Mandatory field.";
    private static final List<String> DATE_PICKER_MODE_OPTIONS = Arrays.asList("calendar", "spinner");

    @Autowired
    public UserAndCatchmentWriter(CatchmentService catchmentService,
                                  LocationRepository locationRepository,
                                  UserService userService,
                                  UserRepository userRepository,
                                  OrganisationConfigService organisationConfigService,
                                  IdpServiceFactory idpServiceFactory,
                                  SubjectTypeService subjectTypeService, ConceptService conceptService, ResetSyncService resetSyncService) {
        this.catchmentService = catchmentService;
        this.locationRepository = locationRepository;
        this.userService = userService;
        this.userRepository = userRepository;
        this.organisationConfigService = organisationConfigService;
        this.idpServiceFactory = idpServiceFactory;
        this.subjectTypeService = subjectTypeService;
        this.conceptService = conceptService;
        this.resetSyncService = resetSyncService;
        this.compoundHeaderPattern = Pattern.compile("^(?<subjectTypeName>.*?)->(?<conceptName>.*)$");
    }

    @Override
    public void write(List<? extends Row> rows) throws Exception {
        if(!CollectionUtils.isEmpty(rows)) {
            validateHeaders(rows.get(0).getHeaders());
            for (Row row : rows) write(row);
        }
    }

    private void validateHeaders(String[] headers) {
        List<String> headerList = Arrays.asList(headers);
        List<String> allErrorMsgs = new ArrayList<>();
        UsersAndCatchmentsHeaders usersAndCatchmentsHeaders = new UsersAndCatchmentsHeaders();
        List<String> expectedStandardHeaders = Arrays.asList(usersAndCatchmentsHeaders.getAllHeaders());
        List<String> syncAttributeHeadersForSubjectTypes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        checkForMissingHeaders(headerList, allErrorMsgs, expectedStandardHeaders, syncAttributeHeadersForSubjectTypes);
        checkForUnknownHeaders(headerList, allErrorMsgs, expectedStandardHeaders, syncAttributeHeadersForSubjectTypes);
        if(!allErrorMsgs.isEmpty()) {
            throw new RuntimeException(String.join(", ", allErrorMsgs));
        }
    }

    private void checkForUnknownHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders, List<String> syncAttributeHeadersForSubjectTypes) {
        headerList.removeIf(StringUtils::isEmpty);
        headerList.removeIf(header -> expectedStandardHeaders.contains(header));
        headerList.removeIf(header -> syncAttributeHeadersForSubjectTypes.contains(header));
        if (!headerList.isEmpty()) {
            allErrorMsgs.add("Unknown headers included in file. Please refer to sample file for valid list of headers.");
        }
    }

    private void checkForMissingHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders, List<String> syncAttributeHeadersForSubjectTypes) {
        if (headerList.isEmpty() || !headerList.containsAll(expectedStandardHeaders) || !headerList.containsAll(syncAttributeHeadersForSubjectTypes)) {
            allErrorMsgs.add("Mandatory columns are missing from uploaded file. Please refer to sample file for the list of mandatory headers.");
        }
    }

    private void write(Row row) throws Exception {
        String fullAddress = row.get(LOCATION_WITH_FULL_HIERARCHY);
        if (fullAddress != null && fullAddress.startsWith(METADATA_ROW_START_STRING)) return;
        String catchmentName = row.get(CATCHMENT_NAME);
        String nameOfUser = row.get(FULL_NAME_OF_USER);
        String username = row.get(USERNAME);
        String email = row.get(EMAIL_ADDRESS);
        String phoneNumber = row.get(MOBILE_NUMBER);
        String language = row.get(PREFERRED_LANGUAGE);
        Locale locale = S.isEmpty(language) ? Locale.en : Locale.valueByName(language);
        Boolean trackLocation = row.getBool(TRACK_LOCATION);
        String datePickerMode = row.get(DATE_PICKER_MODE);
        Boolean beneficiaryMode = row.getBool(ENABLE_BENEFICIARY_MODE);
        String idPrefix = row.get(IDENTIFIER_PREFIX);
        String groupsSpecified = row.get(USER_GROUPS);
        JsonObject syncSettings = constructSyncSettings(row);

        AddressLevel location = locationRepository.findByTitleLineageIgnoreCase(fullAddress)
                .orElseThrow(() -> new Exception(format(
                        "Provided Location does not exist in Avni. Please add it or check for spelling mistakes '%s'", fullAddress)));
        if(!StringUtils.hasLength(catchmentName)) {
            throw new Exception(format("Invalid or Empty value specified for mandatory field %s", CATCHMENT_NAME));
        }
        if(!StringUtils.hasLength(username)) {
            throw new Exception(format("Invalid or Empty value specified for mandatory field %s", USERNAME));
        }
        if(!StringUtils.hasLength(nameOfUser)) {
            throw new Exception(format("Invalid or Empty value specified for mandatory field %s", FULL_NAME_OF_USER));
        }
        if(!StringUtils.hasLength(email)) {
            throw new Exception(format("Invalid or Empty value specified for mandatory field %s", EMAIL_ADDRESS));
        }
        if(!StringUtils.hasLength(phoneNumber)) {
            throw new Exception(format("Invalid or Empty value specified for mandatory field %s", MOBILE_NUMBER));
        }
        if(Objects.isNull(locale)) {
            throw new Exception(format("Provided value '%s' for Preferred Language is invalid;", language));
        }
        if(Objects.isNull(datePickerMode) || !DATE_PICKER_MODE_OPTIONS.contains(datePickerMode)) {
            throw new Exception(format("Provided value '%s' for Date picker mode is invalid;", datePickerMode));
        }

        Catchment catchment = catchmentService.createOrUpdate(catchmentName, location);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        String userSuffix = "@".concat(organisation.getEffectiveUsernameSuffix());
        User.validateUsername(username, userSuffix);
        User user = userRepository.findByUsername(username.trim());
        User currentUser = userService.getCurrentUser();
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.assignUUIDIfRequired();
            user.setUsername(username.trim());
            isNewUser = true;
        }
        User.validateEmail(email);
        user.setEmail(email);
        userService.setPhoneNumber(phoneNumber, user, RegionUtil.getCurrentUserRegion());
        User.validateName(nameOfUser);
        user.setName(nameOfUser.trim());
        if (!isNewUser) resetSyncService.recordSyncAttributeValueChangeForUser(user, catchment.getId(), syncSettings);
        user.setCatchment(catchment);
        user.setOperatingIndividualScope(ByCatchment);
        user.setSyncSettings(syncSettings);

        user.setSettings(new JsonObject()
                .with("locale", locale)
                .with("trackLocation", trackLocation)
                .withEmptyCheckAndTrim("datePickerMode", datePickerMode)
                .with("showBeneficiaryMode", beneficiaryMode)
                .withEmptyCheckAndTrim(UserSettings.ID_PREFIX, idPrefix));

        user.setOrganisationId(organisation.getId());
        user.setAuditInfo(currentUser);
        userService.save(user);
        userService.addToGroups(user, groupsSpecified);
        if (isNewUser) {
            idpServiceFactory.getIdpService(organisation).createUser(user, organisationConfigService.getOrganisationConfig(organisation));
        } else {
            idpServiceFactory.getIdpService(organisation).updateUser(user);
        }
    }

    private JsonObject constructSyncSettings(Row row) {
        List<String> syncAttributeHeadersForSubjectTypes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        Map<String, UserSyncSettings> syncSettingsMap = new HashMap<>();
        for (String saHeader : syncAttributeHeadersForSubjectTypes) {
            updateSyncSettingsFor(saHeader, row, syncSettingsMap);
        }

        JsonObject syncSettings = new JsonObject();
        if (!syncSettingsMap.values().isEmpty())
            syncSettings = syncSettings.with(User.SyncSettingKeys.subjectTypeSyncSettings.name(),
                    new ArrayList<>(syncSettingsMap.values()));

        return syncSettings;
    }

    private void updateSyncSettingsFor(String saHeader, Row row, Map<String, UserSyncSettings> syncSettingsMap) {
        Matcher headerPatternMatcher = compoundHeaderPattern.matcher(saHeader);
        if (headerPatternMatcher.matches()) {
            String conceptName = headerPatternMatcher.group("conceptName");
            String conceptValues = row.get(saHeader);
            if (StringUtils.isEmpty(conceptValues)) {
                throw new ValidationException(String.format("Invalid or Empty value specified for mandatory field %s", saHeader));
            }
            String subjectTypeName = headerPatternMatcher.group("subjectTypeName");
            SubjectType subjectType = subjectTypeService.getByName(subjectTypeName);

            UserSyncSettings userSyncSettings = syncSettingsMap.getOrDefault(subjectType.getUuid(), new UserSyncSettings());
            updateSyncSubjectTypeSettings(subjectType, userSyncSettings);
            updateSyncConceptSettings(subjectType, conceptName, conceptValues, userSyncSettings);

            syncSettingsMap.put(subjectType.getUuid(), userSyncSettings);
        }
    }

    private void updateSyncSubjectTypeSettings(SubjectType subjectType, UserSyncSettings userSyncSettings) {
        String subjectTypeUuid = subjectType.getUuid();
        userSyncSettings.setSubjectTypeUUID(subjectTypeUuid);
    }

    private void updateSyncConceptSettings(SubjectType subjectType, String conceptName, String conceptValues, UserSyncSettings userSyncSettings) {
        Concept concept = conceptService.getByName(conceptName);
        String conceptUuid = concept.getUuid();
        List<String> syncSettingsConceptRawValues = Arrays.asList(conceptValues.split(","));
        List<String> syncSettingsConceptProcessedValues = concept.isCoded() ?
                findSyncSettingCodedConceptValues(syncSettingsConceptRawValues, concept) : syncSettingsConceptRawValues;

        String syncRegistrationConcept1 = subjectType.getSyncRegistrationConcept1();
        if (syncRegistrationConcept1.equals(conceptUuid)) {
            userSyncSettings.setSyncConcept1(conceptUuid);
            userSyncSettings.setSyncConcept1Values(syncSettingsConceptProcessedValues);
        } else {
            userSyncSettings.setSyncConcept2(conceptUuid);
            userSyncSettings.setSyncConcept2Values(syncSettingsConceptProcessedValues);
        }
    }

    private List<String> findSyncSettingCodedConceptValues(List<String> syncSettingsValues, Concept concept) {
        List<String> syncSettingCodedConceptValues = new ArrayList<>();
        for (String syncSettingsValue : syncSettingsValues) {
            Optional<Concept> conceptAnswer = Optional.ofNullable(conceptService.getByName(syncSettingsValue));
            conceptAnswer.orElseThrow(() -> new RuntimeException(String.format("'%s' is not a valid value for the concept '%s'. " +
                            "To input this value, add this as an answer to the coded concept '%s'",
                    syncSettingsValue, concept.getName(), concept.getName())));
            syncSettingCodedConceptValues.add(conceptAnswer.get().getUuid());
        }

        return syncSettingCodedConceptValues;
    }
}

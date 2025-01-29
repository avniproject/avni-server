package org.avni.server.importer.batch.csv.writer;

import com.google.common.collect.Sets;
import jakarta.transaction.Transactional;
import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.UserRepository;
import org.avni.server.domain.Locale;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.importer.batch.csv.writer.header.UsersAndCatchmentsHeaders;
import org.avni.server.importer.batch.model.Row;
import org.avni.server.service.*;
import org.avni.server.util.*;
import org.avni.server.web.request.syncAttribute.UserSyncSettings;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;
import static org.avni.server.domain.OperatingIndividualScope.ByCatchment;
import static org.avni.server.domain.UserSettings.DATE_PICKER_MODE_OPTIONS;
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

    private static final String ERR_MSG_MANDATORY_OR_INVALID_FIELD = "Invalid or Empty value specified for mandatory field %s.";
    private static final String ERR_MSG_LOCATION_FIELD = "Provided Location does not exist in Avni. Please add it or check for spelling mistakes and ensure space between two locations '%s'.";
    private static final String ERR_MSG_LOCALE_FIELD = "Provided value '%s' for Preferred Language is invalid.";
    private static final String ERR_MSG_DATE_PICKER_FIELD = "Provided value '%s' for Date picker mode is invalid.";
    private static final String ERR_MSG_INVALID_PHONE_NUMBER = "Provided value '%s' for phone number is invalid.";
    private static final String ERR_MSG_INVALID_TRACK_LOCATION = "Provided value '%s' for track location is invalid.";
    private static final String ERR_MSG_INVALID_ACTIVE_VALUE = "Provided value '%s' for Active is invalid.";
    private static final String ERR_MSG_INVALID_ENABLE_BENEFICIARY_MODE = "Provided value '%s' for enable beneficiary mode is invalid.";
    private static final String ERR_MSG_UNKNOWN_HEADERS = "Unknown headers - %s included in file. Please refer to sample file for valid list of headers.";
    private static final String ERR_MSG_MISSING_MANDATORY_FIELDS = "Mandatory columns are missing from uploaded file - %s. Please refer to sample file for the list of mandatory headers.";
    private static final String ERR_MSG_INVALID_CONCEPT_ANSWER = "'%s' is not a valid value for the concept '%s'. " +
            "To input this value, add this as an answer to the coded concept '%s'.";
    public static final String METADATA_ROW_START_STRING = "Mandatory field";

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

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    @Override
    public void write(Chunk<? extends Row> chunk) throws IDPException {
        List<? extends Row> rows = chunk.getItems();
        if (!CollectionUtils.isEmpty(rows)) {
            validateHeaders(rows.get(0).getHeaders());
            for (Row row : rows) write(row);
        }
    }

    private void validateHeaders(String[] headers) {
        List<String> headerList = new ArrayList<>(Arrays.asList(headers));
        List<String> allErrorMsgs = new ArrayList<>();
        UsersAndCatchmentsHeaders usersAndCatchmentsHeaders = new UsersAndCatchmentsHeaders();
        List<String> syncAttributeHeadersForSubjectTypes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        checkForMissingHeaders(headerList, allErrorMsgs, Arrays.asList(usersAndCatchmentsHeaders.getMandatoryHeaders()), syncAttributeHeadersForSubjectTypes);
        checkForUnknownHeaders(headerList, allErrorMsgs, Arrays.asList(usersAndCatchmentsHeaders.getAllHeaders()), syncAttributeHeadersForSubjectTypes);
        if (!allErrorMsgs.isEmpty()) {
            throw new RuntimeException(createMultiErrorMessage(allErrorMsgs));
        }
    }

    private void checkForUnknownHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders, List<String> syncAttributeHeadersForSubjectTypes) {
        headerList.removeIf(StringUtils::isEmpty);
        HashSet<String> expectedHeaders = new HashSet<>(expectedStandardHeaders);
        expectedHeaders.addAll(syncAttributeHeadersForSubjectTypes);
        Sets.SetView<String> unknownHeaders = Sets.difference(new HashSet<>(headerList), expectedHeaders);
        if (!unknownHeaders.isEmpty()) {
            allErrorMsgs.add(String.format(ERR_MSG_UNKNOWN_HEADERS, String.join(", ", unknownHeaders)));
        }
    }

    private void checkForMissingHeaders(List<String> headerList, List<String> allErrorMsgs, List<String> expectedStandardHeaders, List<String> expectedSyncAttributeHeadersForSubjectTypes) {
        HashSet<String> expectedHeaders = new HashSet<>(expectedStandardHeaders);
        expectedHeaders.addAll(expectedSyncAttributeHeadersForSubjectTypes);
        HashSet<String> presentHeaders = new HashSet<>(headerList);
        Sets.SetView<String> missingHeaders = Sets.difference(expectedHeaders, presentHeaders);
        if (!missingHeaders.isEmpty()) {
            allErrorMsgs.add(String.format(ERR_MSG_MISSING_MANDATORY_FIELDS, String.join(", ", missingHeaders)));
        }
    }

    private void write(Row row) throws IDPException {
        List<String> rowValidationErrorMsgs = new ArrayList<>();
        String fullAddress = row.get(LOCATION_WITH_FULL_HIERARCHY);
        if (fullAddress != null && fullAddress.startsWith(METADATA_ROW_START_STRING)) return;
        String catchmentName = row.get(CATCHMENT_NAME);
        String nameOfUser = row.get(FULL_NAME_OF_USER);
        String username = row.get(USERNAME);
        String email = row.get(EMAIL_ADDRESS);
        String phoneNumber = row.get(MOBILE_NUMBER);
        String language = row.get(PREFERRED_LANGUAGE);
        Boolean trackLocation = row.getBool(TRACK_LOCATION);
        String datePickerMode = row.get(DATE_PICKER_MODE);
        Boolean beneficiaryMode = row.getBool(ENABLE_BENEFICIARY_MODE);
        String idPrefix = row.get(IDENTIFIER_PREFIX);
        String groupsSpecified = row.get(USER_GROUPS);
        Boolean active = row.getBool(ACTIVE);
        AddressLevel location = locationRepository.findByTitleLineageIgnoreCase(fullAddress).orElse(null);
        Locale locale = S.isEmpty(language) ? Locale.en : Locale.valueByNameIgnoreCase(language);
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        String userSuffix = "@".concat(organisation.getEffectiveUsernameSuffix());
        JsonObject syncSettings = constructSyncSettings(row, rowValidationErrorMsgs);
        validateRowAndAssimilateErrors(rowValidationErrorMsgs, fullAddress, catchmentName, nameOfUser, username, email, phoneNumber, language, datePickerMode, location, locale, userSuffix, trackLocation, row.get(TRACK_LOCATION), beneficiaryMode, row.get(ENABLE_BENEFICIARY_MODE), active, row.get(ACTIVE));
        Catchment catchment = catchmentService.createOrUpdate(catchmentName, location);
        User user = userRepository.findByUsername(username.trim());
        User currentUser = userService.getCurrentUser();
        boolean isNewUser = false;
        if (user == null) {
            user = new User();
            user.assignUUIDIfRequired();
            user.setUsername(username.trim());
            isNewUser = true;
        }
        user.setEmail(email);
        userService.setPhoneNumber(phoneNumber, user, RegionUtil.getCurrentUserRegion());
        user.setName(nameOfUser.trim());
        if (!isNewUser) resetSyncService.recordSyncAttributeValueChangeForUser(user, catchment.getId(), syncSettings);
        user.setCatchment(catchment);
        user.setOperatingIndividualScope(ByCatchment);
        user.setSyncSettings(syncSettings);

        user.setSettings(new JsonObject()
                .with(UserSettings.LOCALE, locale)
                .with(UserSettings.TRACK_LOCATION, UserSettings.createTrackLocation(trackLocation))
                .withEmptyCheckAndTrim(UserSettings.DATE_PICKER_MODE, UserSettings.createDatePickerMode(datePickerMode))
                .with(UserSettings.ENABLE_BENEFICIARY_MODE, UserSettings.createEnableBeneficiaryMode(beneficiaryMode))
                .withEmptyCheckAndTrim(UserSettings.ID_PREFIX, idPrefix));

        user.setOrganisationId(organisation.getId());
        user.setAuditInfo(currentUser);
        userService.save(user);
        userService.addToGroups(user, groupsSpecified);
        IdpService idpService = idpServiceFactory.getIdpService(organisation);
        if (isNewUser && BooleanUtil.getBoolean(active)) {
            idpService.createUser(user, organisationConfigService.getOrganisationConfig(organisation));
        } else if (isNewUser && !BooleanUtil.getBoolean(active)) {
            idpService.createInActiveUser(user, organisationConfigService.getOrganisationConfig(organisation));
        } else if (!isNewUser && BooleanUtil.getBoolean(active)) {
            idpService.activateUser(user);
        } else if (!isNewUser && !BooleanUtil.getBoolean(active)) {
            idpService.disableUser(user);
        }
    }

    private void validateRowAndAssimilateErrors(List<String> rowValidationErrorMsgs, String fullAddress, String catchmentName, String nameOfUser, String username, String email, String phoneNumber, String language, String datePickerMode, AddressLevel location, Locale locale, String userSuffix, Boolean trackLocation, String trackLocationValueProvidedByUser, Boolean beneficiaryMode, String beneficiaryModeValueProvidedByUser, Boolean active, String activeValueProvidedByUser) {
        addErrMsgIfValidationFails(!StringUtils.hasLength(catchmentName), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, CATCHMENT_NAME));
        if (!addErrMsgIfValidationFails(!StringUtils.hasLength(username), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, USERNAME)))
            extractUserUsernameValidationErrMsg(rowValidationErrorMsgs, username, userSuffix);
        if (!addErrMsgIfValidationFails(!StringUtils.hasLength(nameOfUser), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, FULL_NAME_OF_USER)))
            extractUserNameValidationErrMsg(rowValidationErrorMsgs, nameOfUser);
        if (!addErrMsgIfValidationFails(!StringUtils.hasLength(email), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, EMAIL_ADDRESS)))
            extractUserEmailValidationErrMsg(rowValidationErrorMsgs, email);
        if (!addErrMsgIfValidationFails(!StringUtils.hasLength(phoneNumber), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, MOBILE_NUMBER)))
            addErrMsgIfValidationFails(!PhoneNumberUtil.isValidPhoneNumber(phoneNumber, RegionUtil.getCurrentUserRegion()), rowValidationErrorMsgs, format(ERR_MSG_INVALID_PHONE_NUMBER, MOBILE_NUMBER));

        addErrMsgIfValidationFails(ObjectUtils.isEmpty(location), rowValidationErrorMsgs, format(ERR_MSG_LOCATION_FIELD, fullAddress));
        addErrMsgIfValidationFails(ObjectUtils.isEmpty(locale), rowValidationErrorMsgs, format(ERR_MSG_LOCALE_FIELD, language));
        addErrMsgIfValidationFails(!StringUtils.isEmpty(datePickerMode) && !CollectionUtil.containsIgnoreCase(DATE_PICKER_MODE_OPTIONS, datePickerMode), rowValidationErrorMsgs, format(ERR_MSG_DATE_PICKER_FIELD, datePickerMode));

        addErrMsgIfValidationFails(trackLocation == null && !StringUtils.isEmpty(trackLocationValueProvidedByUser), rowValidationErrorMsgs, format(ERR_MSG_INVALID_TRACK_LOCATION, trackLocationValueProvidedByUser));
        addErrMsgIfValidationFails(beneficiaryMode == null && !StringUtils.isEmpty(beneficiaryModeValueProvidedByUser), rowValidationErrorMsgs, format(ERR_MSG_INVALID_ENABLE_BENEFICIARY_MODE, beneficiaryModeValueProvidedByUser));
        addErrMsgIfValidationFails(active == null && !StringUtils.isEmpty(activeValueProvidedByUser), rowValidationErrorMsgs, format(ERR_MSG_INVALID_ACTIVE_VALUE, activeValueProvidedByUser));

        if (!rowValidationErrorMsgs.isEmpty()) {
            throw new RuntimeException(createMultiErrorMessage(rowValidationErrorMsgs));
        }
    }

    private static String createMultiErrorMessage(List<String> errorMsgs) {
        return String.join(" ", errorMsgs);
    }

    private void extractUserNameValidationErrMsg(List<String> rowValidationErrorMsgs, String nameOfUser) {
        try {
            User.validateName(nameOfUser);
        } catch (Exception exception) {
            addErrMsgIfValidationFails(true, rowValidationErrorMsgs, exception.getMessage());
        }
    }

    private void extractUserEmailValidationErrMsg(List<String> rowValidationErrorMsgs, String email) {
        try {
            User.validateEmail(email);
        } catch (Exception exception) {
            addErrMsgIfValidationFails(true, rowValidationErrorMsgs, exception.getMessage());
        }
    }

    private void extractUserUsernameValidationErrMsg(List<String> rowValidationErrorMsgs, String username, String userSuffix) {
        try {
            User.validateUsername(username, userSuffix);
        } catch (Exception exception) {
            addErrMsgIfValidationFails(true, rowValidationErrorMsgs, exception.getMessage());
        }
    }

    private boolean addErrMsgIfValidationFails(boolean validationCheckResult, List<String> rowValidationErrorMsgs, String validationErrorMessage) {
        if (validationCheckResult) {
            rowValidationErrorMsgs.add(validationErrorMessage);
        }
        return validationCheckResult;
    }

    private JsonObject constructSyncSettings(Row row, List<String> rowValidationErrorMsgs) {
        List<String> syncAttributeHeadersForSubjectTypes = subjectTypeService.constructSyncAttributeHeadersForSubjectTypes();
        Map<String, UserSyncSettings> syncSettingsMap = new HashMap<>();
        for (String saHeader : syncAttributeHeadersForSubjectTypes) {
            updateSyncSettingsFor(saHeader, row, syncSettingsMap, rowValidationErrorMsgs);
        }

        JsonObject syncSettings = new JsonObject();
        if (!syncSettingsMap.values().isEmpty())
            syncSettings = syncSettings.with(User.SyncSettingKeys.subjectTypeSyncSettings.name(),
                    new ArrayList<>(syncSettingsMap.values()));

        return syncSettings;
    }

    private void updateSyncSettingsFor(String saHeader, Row row, Map<String, UserSyncSettings> syncSettingsMap, List<String> rowValidationErrorMsgs) {
        Matcher headerPatternMatcher = compoundHeaderPattern.matcher(saHeader);
        if (headerPatternMatcher.matches()) {
            String conceptName = headerPatternMatcher.group("conceptName");
            String conceptValues = row.get(saHeader);
            if (addErrMsgIfValidationFails(StringUtils.isEmpty(conceptValues), rowValidationErrorMsgs, format(ERR_MSG_MANDATORY_OR_INVALID_FIELD, saHeader))) return;

            String subjectTypeName = headerPatternMatcher.group("subjectTypeName");
            SubjectType subjectType = subjectTypeService.getByName(subjectTypeName);

            UserSyncSettings userSyncSettings = syncSettingsMap.getOrDefault(subjectType.getUuid(), new UserSyncSettings());
            updateSyncSubjectTypeSettings(subjectType, userSyncSettings);
            updateSyncConceptSettings(subjectType, conceptName, conceptValues, userSyncSettings, rowValidationErrorMsgs);

            syncSettingsMap.put(subjectType.getUuid(), userSyncSettings);
        }
    }

    private void updateSyncSubjectTypeSettings(SubjectType subjectType, UserSyncSettings userSyncSettings) {
        String subjectTypeUuid = subjectType.getUuid();
        userSyncSettings.setSubjectTypeUUID(subjectTypeUuid);
    }

    private void updateSyncConceptSettings(SubjectType subjectType, String conceptName, String conceptValues, UserSyncSettings userSyncSettings, List<String> rowValidationErrorMsgs) {
        Concept concept = conceptService.getByName(conceptName);
        String conceptUuid = concept.getUuid();
        List<String> syncSettingsConceptRawValues = Arrays.asList(conceptValues.split(","));
        List<String> syncSettingsConceptProcessedValues = concept.isCoded() ?
                findSyncSettingCodedConceptValues(syncSettingsConceptRawValues, concept, rowValidationErrorMsgs) : syncSettingsConceptRawValues;

        String syncRegistrationConcept1 = subjectType.getSyncRegistrationConcept1();
        if (syncRegistrationConcept1.equals(conceptUuid)) {
            userSyncSettings.setSyncConcept1(conceptUuid);
            userSyncSettings.setSyncConcept1Values(syncSettingsConceptProcessedValues);
        } else {
            userSyncSettings.setSyncConcept2(conceptUuid);
            userSyncSettings.setSyncConcept2Values(syncSettingsConceptProcessedValues);
        }
    }

    private List<String> findSyncSettingCodedConceptValues(List<String> syncSettingsValues, Concept concept,
                                                           List<String> rowValidationErrorMsgs) {
        List<String> syncSettingCodedConceptValues = new ArrayList<>();
        for (String syncSettingsValue : syncSettingsValues) {
            Optional<Concept> conceptAnswer = Optional.ofNullable(conceptService.getByName(syncSettingsValue));
            if (conceptAnswer.isPresent()) {
                syncSettingCodedConceptValues.add(conceptAnswer.get().getUuid());
            } else {
                rowValidationErrorMsgs.add(format(ERR_MSG_INVALID_CONCEPT_ANSWER, syncSettingsValue, concept.getName(), concept.getName()));
            }
        }
        return syncSettingCodedConceptValues;
    }
}

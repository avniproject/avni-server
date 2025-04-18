package org.avni.server.importer.batch.csv.writer.header;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.util.ObjectMapperSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class SubjectHeadersCreator extends AbstractHeaders {
    public final static String id = "Id from previous system";
    public final static String subjectTypeHeader = "Subject Type";
    public final static String registrationDate = "Date Of Registration";
    public final static String registrationLocation = "Registration Location";
    public final static String firstName = "First Name";

    public final static String middleName = "Middle Name";

    public final static String lastName = "Last Name";
    public final static String profilePicture = "Profile Picture";
    public final static String totalMembers = "Total Members";
    public final static String dateOfBirth = "Date Of Birth";
    public final static String dobVerified = "Date Of Birth Verified";
    public final static String gender = "Gender";

    private static final Logger logger = LoggerFactory.getLogger(SubjectHeadersCreator.class);
    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final ObjectMapper objectMapper;

    public SubjectHeadersCreator(
            OrganisationConfigService organisationConfigService,
            AddressLevelTypeRepository addressLevelTypeRepository) {
        this.organisationConfigService = organisationConfigService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.objectMapper = ObjectMapperSingleton.getObjectMapper();
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping, Mode mode) {
        SubjectType subjectType = formMapping.getSubjectType();
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(subjectTypeHeader, subjectType.getName(), true, null, null, null, false));
        fields.add(new HeaderField(registrationDate, "", true, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
        fields.add(new HeaderField(registrationLocation, "", false, null, "Format: (21.5135243,85.6731848)", null));

        if (subjectType.isPerson()) {
            fields.add(new HeaderField(firstName, "", true, null, null, null));
            if (subjectType.isAllowMiddleName()) {
                fields.add(new HeaderField(middleName, "", false, null, null, null));
            }
            fields.add(new HeaderField(lastName, "", true, null, null, null));
            if (subjectType.isAllowProfilePicture())
                fields.add(new HeaderField(profilePicture, "", false, null, null, null));
            fields.add(new HeaderField(dateOfBirth, "", false, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
            fields.add(new HeaderField(dobVerified, "Default value: false", false, "Allowed values: {true, false}", null, null));
            fields.add(new HeaderField(gender, "", true, "Allowed values: {Female, Male, Other}", null, null));
        } else if (subjectType.isHousehold()) {
            fields.add(new HeaderField(firstName, "", true, null, null, null));
            fields.add(new HeaderField(profilePicture, "", false, null, null, null));
            fields.add(new HeaderField(totalMembers, "", true, "Allowed values: Any number", null, null));
        } else {
            fields.add(new HeaderField(firstName, "", true, null, null, null));
            fields.add(new HeaderField(profilePicture, "", false, null, null, null));
        }

        fields.addAll(generateAddressFields(formMapping));
        fields.addAll(generateConceptFields(formMapping));
        fields.addAll(generateDecisionConceptFields(formMapping.getForm()));

        return fields;
    }

    private List<HeaderField> generateAddressFields(FormMapping formMapping) {
        List<String> addressHeaders;
        try {
            boolean hasCustomLocations = !organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())
                    .equals(Collections.emptyList());

            addressHeaders = hasCustomLocations
                    ? getCustomLocationHeaders(formMapping)
                    : getDefaultAddressHeaders();

            if (addressHeaders.isEmpty()) {
                logger.warn("No address headers found for subject type: {}", formMapping.getSubjectType().getName());
                return Collections.emptyList();
            }
        } catch (Exception e) {
            logger.error("Error retrieving address headers", e);
            addressHeaders = getDefaultAddressHeaders();
        }

        if (addressHeaders.isEmpty()) {
            return Collections.emptyList();
        }

        // Mark all address fields as mandatory since they form a hierarchical structure
        // and if the lowest level is required, then the higher levels are implicitly required too
        return addressHeaders.stream()
                .map(header -> new HeaderField(header, "", true, null, null, null, false))
                .collect(Collectors.toList());
    }

    private List<String> getDefaultAddressHeaders() {
        return addressLevelTypeRepository.getAllNames();
    }

    private List<String> getCustomLocationHeaders(FormMapping formMapping) {
        try {
            return extractLocationHeaders(findMatchingLocation(formMapping));
        } catch (Exception e) {
            logger.error("Error processing custom registration locations", e);
            return getDefaultAddressHeaders();
        }
    }

    private JsonNode getCustomRegistrationLocations() {
        try {
            return objectMapper.valueToTree(
                    organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString())
            );
        } catch (Exception e) {
            logger.error("Failed to retrieve custom registration locations", e);
            return objectMapper.createArrayNode(); // Return empty array instead of null
        }
    }

    private Optional<JsonNode> findMatchingLocation(FormMapping formMapping) {
        JsonNode customRegistrationLocations = getCustomRegistrationLocations();
        if (customRegistrationLocations == null || customRegistrationLocations.isEmpty()) {
            logger.warn("No custom registration locations configured");
            return Optional.empty();
        }

        String subjectTypeUuid = formMapping.getSubjectType().getUuid();

        for (JsonNode location : customRegistrationLocations) {
            if (location == null || !location.has("subjectTypeUUID")) {
                continue;
            }

            String locationSubjectTypeUuid = location.get("subjectTypeUUID").asText();
            if (subjectTypeUuid.equals(locationSubjectTypeUuid)) {
                return Optional.of(location);
            }
        }

        return Optional.empty();
    }

    private List<String> extractLocationHeaders(Optional<JsonNode> locationOpt) {
        if (!locationOpt.isPresent()) {
            logger.warn("No matching location found for the subject type");
            return getDefaultAddressHeaders();
        }

        JsonNode location = locationOpt.get();
        if (!location.has("locationTypeUUIDs")) {
            logger.warn("Location missing locationTypeUUIDs field");
            return getDefaultAddressHeaders();
        }

        JsonNode locationTypeUUIDsNode = location.get("locationTypeUUIDs");
        if (locationTypeUUIDsNode == null || locationTypeUUIDsNode.isEmpty()) {
            logger.warn("Empty locationTypeUUIDs");
            return getDefaultAddressHeaders();
        }

        try {
            List<String> locationTypeUUIDs = objectMapper.convertValue(
                    locationTypeUUIDsNode,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            return locationTypeUUIDs.stream()
                    .flatMap(uuid -> addressLevelTypeRepository.getAllParentNames(uuid).stream())
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (Exception e) {
            logger.error("Error processing location type UUIDs", e);
            return getDefaultAddressHeaders();
        }
    }
}

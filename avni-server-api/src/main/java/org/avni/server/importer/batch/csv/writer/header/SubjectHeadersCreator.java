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
        fields.add(new HeaderField(registrationLocation, "", false, null, "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)", null));

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

    public List<HeaderField> generateAddressFields(FormMapping formMapping) {
        List<String> addressHeaders = hasCustomLocations()
                ? getCustomLocationHeaders(formMapping)
                : getDefaultAddressHeaders();

        if (addressHeaders.isEmpty()) {
            logger.error("No address headers found for subject type: {}", formMapping.getSubjectType().getName());
            return Collections.emptyList();
        }

        // Mark all address fields as mandatory since they form a hierarchical structure
        // and if the lowest level is required, then the higher levels are implicitly required too
        return addressHeaders.stream()
                .map(header -> new HeaderField(header, "", true, null, null, null, true))
                .collect(Collectors.toList());
    }

    private boolean hasCustomLocations() {
        JsonNode customRegistrationLocations = getCustomRegistrationLocations();
        if (customRegistrationLocations == null || customRegistrationLocations.isEmpty()) {
            logger.debug("No custom registration locations configured");
            return false;
        }
        return true;
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
        if (!locationOpt.isPresent()
                || !locationOpt.get().has("locationTypeUUIDs")
                || locationOpt.get().get("locationTypeUUIDs") == null
                || locationOpt.get().get("locationTypeUUIDs").isEmpty()) {
            logger.warn("Empty locationTypeUUIDs");
            return getDefaultAddressHeaders();
        }

        try {
            List<String> locationTypeUUIDs = objectMapper.convertValue(
                    locationOpt.get().get("locationTypeUUIDs"),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
            );

            List<List<String>> listOfParentNameList = locationTypeUUIDs.stream()
                    .map(addressLevelTypeRepository::getAllParentNames)
                    .collect(Collectors.toList());

            // Remove any list that is a contiguous sublist of another
            List<List<String>> filtered = listOfParentNameList.stream()
                    .filter(current -> listOfParentNameList.stream()
                            .filter(other -> !other.equals(current)) // Filter out comparison with self
                            .noneMatch(other -> other.size() > current.size() &&
                                    Collections.indexOfSubList(other, current) >= 0)) // Check if current is not a sublist of any other
                    .collect(Collectors.toList());

            // To reverse the order, collect to a list and then reverse it
            List<String> flatList = filtered.stream()
                    .flatMap(List::stream)
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(flatList);
            return flatList;
        } catch (Exception e) {
            logger.error("Error processing location type UUIDs", e);
            return getDefaultAddressHeaders();
        }
    }
}

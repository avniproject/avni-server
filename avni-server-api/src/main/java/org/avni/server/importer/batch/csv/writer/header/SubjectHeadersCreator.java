package org.avni.server.importer.batch.csv.writer.header;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.ImportHelperService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SubjectHeadersCreator extends AbstractHeaders{
    public final static String id = "Id from previous system";
    public final static String subjectTypeHeader = "Subject Type";
    public final static String registrationDate = "Date Of Registration";
    public final static String registrationLocation = "Registration Location";
    public final static String firstName = "First Name";

    public final static String middleName = "Middle Name";

    public final static String lastName = "Last Name";
    public final static String profilePicture = "Profile Picture";
    public  final static String totalMembers = "Total Members";
    public final static String dateOfBirth = "Date Of Birth";
    public final static String dobVerified = "Date Of Birth Verified";
    public final static String gender = "Gender";

    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final FormMappingRepository formMappingRepository;

    public SubjectHeadersCreator(
            ImportHelperService importHelperService,
            OrganisationConfigService organisationConfigService,
            AddressLevelTypeRepository addressLevelTypeRepository,
            FormMappingRepository formMappingRepository,
            List<FieldDescriptorStrategy> strategyList) {
        super(importHelperService,strategyList);
        this.organisationConfigService = organisationConfigService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.formMappingRepository = formMappingRepository;
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping) {
        SubjectType subjectType = formMapping.getSubjectType();
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        if (formMappingRepository.getSubjectTypesMappedToAForm(formMapping.getFormUuid()).size() > 1) {
            fields.add(new HeaderField(subjectTypeHeader, subjectType.getName(), true, null, null, null,false));
        }
        fields.add(new HeaderField(registrationDate, "", true, null, "Format: DD-MM-YYYY", null));
        fields.add(new HeaderField(registrationLocation, "", false, null, "Format: (21.5135243,85.6731848)", null));

        if (subjectType.isPerson()) {
            fields.add(new HeaderField(firstName, "", true, null, null, null));
            if (subjectType.isAllowMiddleName()) {
                fields.add(new HeaderField(middleName, "", false, null, null, null));
            }
            fields.add(new HeaderField(lastName, "", true, null, null, null));
            fields.add(new HeaderField(profilePicture, "", false, null, null, null));
            fields.add(new HeaderField(dateOfBirth, "", false, null, "Format: DD-MM-YYYY", null));
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

        return fields;
    }

    private List<HeaderField> generateAddressFields(FormMapping formMapping) {
        List<String> headers = !organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString()).equals(Collections.emptyList())
                ? fromCustomLocations(formMapping).getHeaders()
                : addressLevelTypeRepository.getAllNames();
        return headers.stream()
                .map(header -> new HeaderField(header, "", false, null, null, null,false))
                .collect(Collectors.toList());
    }

    private AddressConfig fromCustomLocations(FormMapping formMapping) {
        List<String> headers = new ArrayList<>();
        ObjectMapper mapper = ObjectMapperSingleton.getObjectMapper();
        try {
            JsonNode customRegistrationLocations = mapper.valueToTree(organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString()));
            boolean hasCustomLocations = false;
            for (JsonNode location : customRegistrationLocations) {
                String subjectTypeUUID = location.get("subjectTypeUUID").asText();
                if (formMapping.getSubjectType().getUuid().equals(subjectTypeUUID)) {
                    JsonNode locationTypeUUIDsNode = location.get("locationTypeUUIDs");
                    List<String> locationTypeUUIDs = mapper.convertValue(locationTypeUUIDsNode,
                            mapper.getTypeFactory().constructCollectionType(List.class, String.class));
                    Set<String> uniqueHeaders = locationTypeUUIDs.stream()
                            .flatMap(uuid -> addressLevelTypeRepository.getAllParentNames(uuid).stream())
                            .collect(Collectors.toSet());
                    headers.addAll(uniqueHeaders);
                    hasCustomLocations = true;
                    break;
                }
            }
            if (!hasCustomLocations) {
                return defaultConfig();
            }
        } catch (Exception e) {
            return defaultConfig();
        }
        return new AddressConfig(headers, headers.size());
    }

    private AddressConfig defaultConfig() {
        List<String> headers = addressLevelTypeRepository.getAllNames();
        return new AddressConfig(headers, headers.size());
    }
}
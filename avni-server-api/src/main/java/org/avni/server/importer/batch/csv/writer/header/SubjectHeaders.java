package org.avni.server.importer.batch.csv.writer.header;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormMapping;
import org.avni.server.application.KeyType;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.ImportHelperService;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.util.ObjectMapperSingleton;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SubjectHeaders implements Headers{
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

    private final ImportHelperService importHelperService;
    private final OrganisationConfigService organisationConfigService;
    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final FormMappingRepository formMappingRepository;

    public SubjectHeaders(
            ImportHelperService importHelperService,
            OrganisationConfigService organisationConfigService,
            AddressLevelTypeRepository addressLevelTypeRepository,
            FormMappingRepository formMappingRepository) {
        this.importHelperService = importHelperService;
        this.organisationConfigService = organisationConfigService;
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.formMappingRepository = formMappingRepository;
    }

    @Override
    public String[] getAllHeaders(SubjectType subjectType, FormMapping formMapping) {
        return buildFields(subjectType, formMapping).stream()
                .map(SubjectField::getHeader)
                .toArray(String[]::new);
    }


    @Override
    public String[] getAllHeaders() {
        throw new UnsupportedOperationException("Use getAllHeaders(SubjectType, FormMapping) with runtime data instead");
    }

    public String[] getAllDescriptions(SubjectType subjectType, FormMapping formMapping) {
        return buildFields(subjectType, formMapping).stream()
                .map(SubjectField::getDescription)
                .toArray(String[]::new);
    }

    private List<SubjectField> buildFields(SubjectType subjectType, FormMapping formMapping) {
        List<SubjectField> fields = new ArrayList<>();

        fields.add(new SubjectField(id, "Can be used to later identify the entry", false, null, null));
        if (formMappingRepository.getSubjectTypesMappedToAForm(formMapping.getFormUuid()).size() > 1) {
            fields.add(new SubjectField(subjectTypeHeader, subjectType.getName(), true, null, null));
        }
        fields.add(new SubjectField(registrationDate, "", true, null, "Format: DD-MM-YYYY"));
        fields.add(new SubjectField(registrationLocation, "", false, null, "Format: (21.5135243,85.6731848)"));

        if (subjectType.isPerson()) {
            fields.add(new SubjectField(firstName, "", true, null, null));
            if (subjectType.isAllowMiddleName()) {
                fields.add(new SubjectField(middleName, "", false, null, null));
            }
            fields.add(new SubjectField(lastName, "", true, null, null));
            fields.add(new SubjectField(profilePicture, "", false, null, null));
            fields.add(new SubjectField(dateOfBirth, "", false, null, "Format: DD-MM-YYYY"));
            fields.add(new SubjectField(dobVerified, "Default value: false", false, "Allowed values: {true, false}", null));
            fields.add(new SubjectField(gender, "", true, "Allowed values: {Female, Male, Other}", null));
        } else if (subjectType.isHousehold()) {
            fields.add(new SubjectField(firstName, "", true, null, null));
            fields.add(new SubjectField(profilePicture, "", false, null, null));
            fields.add(new SubjectField(totalMembers, "", true, "Allowed values: Any number", null));
        } else {
            fields.add(new SubjectField(firstName, "", true, null, null));
            fields.add(new SubjectField(profilePicture, "", false, null, null));
        }

        fields.addAll(generateAddressFields(formMapping));
        fields.addAll(generateConceptFields(formMapping));

        return fields;
    }

    private List<SubjectField> generateAddressFields(FormMapping formMapping) {
        List<String> headers = !organisationConfigService.getSettingsByKey(KeyType.customRegistrationLocations.toString()).equals(Collections.emptyList())
                ? AddressConfig.fromCustomLocations(formMapping, organisationConfigService, addressLevelTypeRepository).headers()
                : addressLevelTypeRepository.getAllNames();
        return headers.stream()
                .map(header -> new SubjectField(header, "", false, null, null))
                .collect(Collectors.toList());
    }

    private List<SubjectField> generateConceptFields(FormMapping formMapping) {
        return formMapping.getForm().getApplicableFormElements().stream()
                .filter(fe -> !ConceptDataType.isQuestionGroup(fe.getConcept().getDataType()))
                .map(this::mapFormElementToField)
                .collect(Collectors.toList());
    }

    private SubjectField mapFormElementToField(FormElement fe) {
        Concept concept = fe.getConcept();
        String header = importHelperService.getHeaderName(fe);
        String allowedValues = null;
        String format = null;

        if (ConceptDataType.matches(ConceptDataType.Coded, concept.getDataType())) {
            allowedValues = "Allowed values: {" + concept.getConceptAnswers().stream()
                    .map(ca -> ca.getAnswerConcept().getName())
                    .collect(Collectors.joining(", ")) + "}";
        } else if (ConceptDataType.matches(ConceptDataType.Date, concept.getDataType())) {
            format = "Format: DD-MM-YYYY";
        } else if (ConceptDataType.matches(ConceptDataType.Numeric, concept.getDataType())) {
            allowedValues = "Allowed values: Any number";
            if (concept.getHighAbsolute() != null) allowedValues = "Max value allowed: " + concept.getHighAbsolute();
            if (concept.getLowAbsolute() != null) allowedValues = "Min value allowed: " + concept.getLowAbsolute();
    }

        return new SubjectField(header, "", fe.isMandatory(), allowedValues, format);
    }

    private static class SubjectField {
        private final String header;
        private final String description;
        private final boolean mandatory;
        private final String allowedValues;
        private final String format;

        public SubjectField(String header, String description, boolean mandatory, String allowedValues, String format) {
            this.header = header;
            this.description = description;
            this.mandatory = mandatory;
            this.allowedValues = allowedValues;
            this.format = format;
        }

        public String getHeader() {
            return header;
        }

        public String getDescription() {
            StringBuilder desc = new StringBuilder();
            String baseDesc = description.isEmpty() ? "" : description;
            desc.append(baseDesc);
            if (!header.equals(subjectTypeHeader)) {
                desc.insert(0, mandatory ? "| Mandatory | " : "| Optional | ");
            }
            if (allowedValues != null) {
                desc.append(" ").append(allowedValues);
            }
            if (format != null) {
                desc.append(" ").append(format);
            }
            String result = desc.toString();
            return "\"" + result.replace("\"", "\"\"") + "\"";
        }
}

    private record AddressConfig(List<String> headers, int count) {
        static AddressConfig fromCustomLocations(FormMapping formMapping, OrganisationConfigService organisationConfigService, AddressLevelTypeRepository addressLevelTypeRepository) {
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
                        headers.addAll(locationTypeUUIDs.stream()
                                .flatMap(uuid -> addressLevelTypeRepository.getAllParentNames(uuid).stream())
                                .toList());
                        hasCustomLocations = true;
                        break;
                    }
                }
                if (!hasCustomLocations) {
                    headers.addAll(addressLevelTypeRepository.getAllNames());
                }
            } catch (Exception e) {
                headers.addAll(addressLevelTypeRepository.getAllNames());
            }
            return new AddressConfig(headers, headers.size());
        }

        static AddressConfig defaultConfig(AddressLevelTypeRepository addressLevelTypeRepository) {
            List<String> headers = addressLevelTypeRepository.getAllNames();
            return new AddressConfig(headers, headers.size());
        }
    }
}
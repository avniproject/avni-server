package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;
import org.avni.server.config.InvalidConfigurationException;
import org.avni.server.dao.AddressLevelTypeRepository;
import org.avni.server.domain.AddressLevelType;
import org.avni.server.domain.SubjectType;
import org.avni.server.service.AddressLevelService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SubjectHeadersCreator extends AbstractHeaders {
    public final static String id = "Id from previous system";
    public final static String subjectTypeHeader = "Subject Type";
    public final static String registrationDate = "Date Of Registration";
    public final static String registrationLocation = "Registration Location";
    public final static String subjectLocation = "Subject Location";
    public final static String firstName = "First Name";

    public final static String middleName = "Middle Name";

    public final static String lastName = "Last Name";
    public final static String profilePicture = "Profile Picture";
    public final static String totalMembers = "Total Members";
    public final static String dateOfBirth = "Date Of Birth";
    public final static String dobVerified = "Date Of Birth Verified";
    public final static String gender = "Gender";

    private final AddressLevelTypeRepository addressLevelTypeRepository;
    private final AddressLevelService addressLevelService;

    public SubjectHeadersCreator(
            AddressLevelTypeRepository addressLevelTypeRepository,
            AddressLevelService addressLevelService) {
        this.addressLevelTypeRepository = addressLevelTypeRepository;
        this.addressLevelService = addressLevelService;
    }

    @Override
    protected List<HeaderField> buildFields(FormMapping formMapping, Object mode) throws InvalidConfigurationException {
        SubjectType subjectType = formMapping.getSubjectType();
        List<HeaderField> fields = new ArrayList<>();

        fields.add(new HeaderField(id, "Can be used to later identify the entry", false, null, null, null));
        fields.add(new HeaderField(subjectTypeHeader, subjectType.getName(), true, null, null, null, true));
        fields.add(new HeaderField(registrationDate, "", true, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
        fields.add(new HeaderField(registrationLocation, "", false, null, "Format: latitude,longitude in decimal degrees (e.g., 19.8188,83.9172)", null));
        fields.add(new HeaderField(subjectLocation, "", false, null, "Format: latitude,longitude,accuracy in decimal degrees (e.g., 19.8188,83.9172,5.0)", null));

        if (subjectType.isPerson()) {
            fields.add(new HeaderField(firstName, "", true, null, null, null));
            if (subjectType.isAllowMiddleName()) {
                fields.add(new HeaderField(middleName, "", false, null, null, null));
            }
            fields.add(new HeaderField(lastName, "", true, null, null, null));
            if (subjectType.isAllowProfilePicture())
                fields.add(new HeaderField(profilePicture, "", false, null, null, null));
            fields.add(new HeaderField(dateOfBirth, "", true, null, "Format: DD-MM-YYYY or YYYY-MM-DD", null));
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

        fields.addAll(generateAddressFields(mode));
        fields.addAll(generateConceptFields(formMapping, false));
        fields.addAll(generateDecisionConceptFields(formMapping.getForm()));

        return fields;
    }

    public List<HeaderField> generateAddressFields(Object mode) {
        String locationHierarchy = (String) mode;

        List<Long> hierarchyIds = Arrays.stream(locationHierarchy.split("\\."))
                .map(Long::parseLong)
                .collect(Collectors.toList());

        List<AddressLevelType> addressLevelTypes = addressLevelTypeRepository.findAllByIdIn(hierarchyIds);

        List<String> orderedNames = addressLevelTypes.stream()
                .sorted(Comparator.comparingDouble(AddressLevelType::getLevel).reversed())
                .map(AddressLevelType::getName)
                .toList();

        return orderedNames.stream()
                .map(name -> new HeaderField(name, "", true, null, null, null, true))
                .collect(Collectors.toList());
    }
}

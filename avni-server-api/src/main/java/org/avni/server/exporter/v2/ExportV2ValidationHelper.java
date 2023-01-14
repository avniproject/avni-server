package org.avni.server.exporter.v2;

import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportOutput;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ExportV2ValidationHelper implements LongitudinalExportRequestFieldNameConstants{
    private final static Pattern UUID_REGEX_PATTERN =
            Pattern.compile("^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89aAbB][a-f0-9]{3}-[a-f0-9]{12}$");

    public static final String INDIVIDUAL = "Individual";
    public static final String ENCOUNTER = "Encounter";
    public static final String GROUP_SUBJECT = "Group Subject";
    public static final String GROUP_SUBJECT_ENCOUNTER = "Group Subject Encounter";
    public static final String PROGRAM_ENROLMENT = "Program Enrolment";
    public static final String PROGRAM_ENCOUNTER = "Program Encounter";

    private static List<String> validRegistrationFields = Arrays.asList(
            ID,
            UUID,
            FIRST_NAME,
            MIDDLE_NAME,
            LAST_NAME,
            DATE_OF_BIRTH,
            REGISTRATION_DATE,
            GENDER,
            CREATED_BY,
            CREATED_DATE_TIME,
            LAST_MODIFIED_BY,
            LAST_MODIFIED_DATE_TIME,
            VOIDED);

    private static List<String> validEnrolmentFields = Arrays.asList(
            ID,
            UUID,
            ENROLMENT_DATE_TIME,
            PROGRAM_EXIT_DATE_TIME,
            CREATED_BY,
            CREATED_DATE_TIME,
            LAST_MODIFIED_BY,
            LAST_MODIFIED_DATE_TIME,
            VOIDED);


    private static List<String> validEncounterFields = Arrays.asList(
            ID,
            UUID,
            NAME,
            EARLIEST_VISIT_DATE_TIME,
            MAX_VISIT_DATE_TIME,
            ENCOUNTER_DATE_TIME,
            CANCEL_DATE_TIME,
            CREATED_BY,
            CREATED_DATE_TIME,
            LAST_MODIFIED_BY,
            LAST_MODIFIED_DATE_TIME,
            VOIDED);

    private void validateFields(List<String> errorList, String entityName, List<String> requestFields, List<String> allowedFields) {
        requestFields.removeAll(allowedFields);
        String invalidFields = requestFields.stream().filter(this::isNotUUID).collect(Collectors.joining(","));
        if(StringUtils.hasText(invalidFields)) {
            errorList.add("Invalid fields specified for "+ entityName+" : "+invalidFields);
        }
    }

    private boolean isNotUUID(String str) {
        if (!StringUtils.hasText(str)) {
            return false;
        }
        return !UUID_REGEX_PATTERN.matcher(str).matches();
    }

    public void validateRegistrationHeaders(List<String> errorList, String entityName, List<String> requestFields) {
        validateFields(errorList, entityName, requestFields, validRegistrationFields);
    }

    public void validateEncounterHeaders(List<String> errorList, String entityName, List<String> requestFields) {
        validateFields(errorList, entityName, requestFields, validEncounterFields);
    }

    public void validateEnrolmentHeaders(List<String> errorList, String entityName, List<String> requestFields) {
        validateFields(errorList, entityName, requestFields, validEnrolmentFields);
    }

    public boolean validateIfDateFilterIsNotSpecified(ExportEntityType entityType) {
        return entityType.isDateEmpty();
    }

    public List<String> validate(ExportOutput exportOutput) {
        List<String> errorList = new ArrayList<>();
        if(validateIfDateFilterIsNotSpecified(exportOutput)) {
            errorList.add("Individual Registration Date isn't specified");
        }
        validateRegistrationHeaders(errorList, INDIVIDUAL, exportOutput.getFields());

        exportOutput.getEncounters().forEach(enc -> {
            validateEncounterHeaders(errorList, ENCOUNTER, enc.getFields());
        });

        exportOutput.getPrograms().forEach(enr -> {
            validateEnrolmentHeaders(errorList, PROGRAM_ENROLMENT, enr.getFields());
        });

        exportOutput.getPrograms().stream().flatMap(enr -> enr.getEncounters().stream()).forEach(enc -> {
            validateEncounterHeaders(errorList, PROGRAM_ENCOUNTER, enc.getFields());
        });

        exportOutput.getGroups().forEach(grp -> {
                    validateRegistrationHeaders(errorList, GROUP_SUBJECT, grp.getFields());
        });

        exportOutput.getGroups().stream().flatMap(grp -> grp.getEncounters().stream()).forEach(enc -> {
            validateEncounterHeaders(errorList, GROUP_SUBJECT_ENCOUNTER, enc.getFields());
        });

        return errorList;
    }
}

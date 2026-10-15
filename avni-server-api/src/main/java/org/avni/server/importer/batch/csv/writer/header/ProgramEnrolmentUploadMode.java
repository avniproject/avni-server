package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.util.BadRequestError;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.stream.Collectors;

public enum ProgramEnrolmentUploadMode {
    UPLOAD_ENROLMENT("upload_enrolments"),
    UPLOAD_EXITED_ENROLMENT("upload_exited_enrolments"),
    EXIT_ENROLMENT("exit_enrolments");

    private final String value;

    ProgramEnrolmentUploadMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ProgramEnrolmentUploadMode fromString(String value) {
        for (ProgramEnrolmentUploadMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }

    /**
     * A missing or blank mode means a normal enrolment upload. A value that is not a known mode is rejected,
     * so that a typo never quietly runs a normal enrolment upload.
     */
    public static ProgramEnrolmentUploadMode parseOrDefault(String value) {
        if (!StringUtils.hasText(value)) {
            return UPLOAD_ENROLMENT;
        }
        ProgramEnrolmentUploadMode mode = fromString(value.trim());
        if (mode == null) {
            String validValues = Arrays.stream(values()).map(ProgramEnrolmentUploadMode::getValue).collect(Collectors.joining(", "));
            throw new BadRequestError("Unknown programEnrolmentUploadMode '%s'. Valid values: %s", value, validValues);
        }
        return mode;
    }
}

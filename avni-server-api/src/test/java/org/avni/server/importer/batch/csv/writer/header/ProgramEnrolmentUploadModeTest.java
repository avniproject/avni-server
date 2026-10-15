package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.util.BadRequestError;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ProgramEnrolmentUploadModeTest {

    @Test
    public void parsesExitEnrolments() {
        assertEquals(ProgramEnrolmentUploadMode.EXIT_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault("exit_enrolments"));
    }

    @Test
    public void parsesExistingModes() {
        assertEquals(ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault("upload_enrolments"));
        assertEquals(ProgramEnrolmentUploadMode.UPLOAD_EXITED_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault("upload_exited_enrolments"));
    }

    @Test
    public void blankMeansUploadEnrolment() {
        assertEquals(ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault(null));
        assertEquals(ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault(""));
        assertEquals(ProgramEnrolmentUploadMode.UPLOAD_ENROLMENT, ProgramEnrolmentUploadMode.parseOrDefault("  "));
    }

    @Test
    public void unknownThrowsBadRequest() {
        BadRequestError error = assertThrows(BadRequestError.class, () -> ProgramEnrolmentUploadMode.parseOrDefault("bogus"));
        assertEquals("Unknown programEnrolmentUploadMode 'bogus'. Valid values: upload_enrolments, upload_exited_enrolments, exit_enrolments", error.getMessage());
    }
}

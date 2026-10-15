package org.avni.server.web;

import org.avni.server.application.FormMapping;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.GroupRepository;
import org.avni.server.domain.Group;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.ProgramBuilder;
import org.avni.server.domain.metadata.SubjectTypeBuilder;
import org.avni.server.service.builder.TestDataSetupService;
import org.avni.server.service.builder.TestProgramService;
import org.avni.server.service.builder.TestSubjectTypeService;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.response.ImportSampleCheckResponse;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.jupiter.api.Assertions.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ImportControllerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String UNKNOWN_MODE_MESSAGE = "Unknown programEnrolmentUploadMode 'bogus'. Valid values: upload_enrolments, upload_exited_enrolments, exit_enrolments";

    @Autowired
    private ImportController importController;
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private TestSubjectTypeService testSubjectTypeService;
    @Autowired
    private TestProgramService testProgramService;
    @Autowired
    private GroupRepository groupRepository;

    private String programEnrolmentUploadType;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        Group group = testDataSetupService.setupOrganisation().getGroup();
        group.setHasAllPrivileges(true);
        groupRepository.save(group);
        SubjectType subjectType = testSubjectTypeService.createWithDefaults(new SubjectTypeBuilder().setMandatoryFieldsForNewEntity().build());
        FormMapping enrolmentFormMapping = testProgramService.addProgramAndGetFormMapping(new ProgramBuilder().withName("Program1").build(), subjectType);
        programEnrolmentUploadType = String.format("ProgramEnrolment---%s---%s", enrolmentFormMapping.getProgram().getName(), subjectType.getName());
    }

    @Test
    public void sampleDownload_unknownMode_throwsBadRequest() {
        BadRequestError error = assertThrows(BadRequestError.class, () ->
                importController.getSampleImportFile(programEnrolmentUploadType, null, null, null, "bogus", new MockHttpServletResponse()));
        assertEquals(UNKNOWN_MODE_MESSAGE, error.getMessage());
    }

    @Test
    public void sampleCheck_unknownMode_notDownloadableWithMessage() {
        ResponseEntity<ImportSampleCheckResponse> response = importController.checkSampleImportFileIsDownloadable(programEnrolmentUploadType, null, null, null, "bogus", null);
        assertFalse(response.getBody().success());
        assertEquals(UNKNOWN_MODE_MESSAGE, response.getBody().message());
    }

    @Test
    public void sampleCheck_emptyMode_isDownloadable() {
        ResponseEntity<ImportSampleCheckResponse> response = importController.checkSampleImportFileIsDownloadable(programEnrolmentUploadType, null, null, null, "", null);
        assertTrue(response.getBody().success(), response.getBody().message());
    }

    @Test
    public void upload_unknownMode_returnsBadRequestWithMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "exits.csv", "text/csv", "Program Enrolment Id,Exit Date\n".getBytes());
        ResponseEntity<?> response = importController.importFile(file, programEnrolmentUploadType, false, "", "", "", "bogus");
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(UNKNOWN_MODE_MESSAGE, response.getBody());
    }
}

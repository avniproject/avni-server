package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class MetadataDiffControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void testCompareMetadataZips() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file1", "file1.zip", MediaType.MULTIPART_FORM_DATA_VALUE, "zip file content".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file2", "file2.zip", MediaType.MULTIPART_FORM_DATA_VALUE, "zip file content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/compare-metadata")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk());
    }
}
package org.avni.server.web;

import jakarta.transaction.Transactional;
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
@Transactional
public class MetadataDiffControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    @Test
    public void testCompareMetadataZips() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("candidateBundle", "file1.zip", MediaType.MULTIPART_FORM_DATA_VALUE, "zip file content".getBytes());
        mockMvc.perform(MockMvcRequestBuilders.multipart("/web/bundle/findChanges")
                        .file(file1))
                .andExpect(status().isOk());
    }
}

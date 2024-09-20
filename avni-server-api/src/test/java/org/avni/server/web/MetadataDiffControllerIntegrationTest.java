package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


public class MetadataDiffControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Test
    public void testCompareMetadataZips() throws Exception {
        setUser("demo-user");
        MockMultipartFile file1 = new MockMultipartFile("file1", "file1.zip", MediaType.MULTIPART_FORM_DATA_VALUE, "zip file content".getBytes());
        MockMultipartFile file2 = new MockMultipartFile("file2", "file2.zip", MediaType.MULTIPART_FORM_DATA_VALUE, "zip file content".getBytes());

        mockMvc.perform(MockMvcRequestBuilders.multipart("/api/compare-metadata")
                        .file(file1)
                        .file(file2))
                .andExpect(status().isOk());
    }
}
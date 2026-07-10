package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.domain.OrgStorageCredential;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class OrgStorageCredentialRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private OrgStorageCredentialRepository orgStorageCredentialRepository;
    @Autowired
    private DownloadableContentRepository downloadableContentRepository;

    @Before
    public void setUp() {
        testDataSetupService.setupOrganisation();
    }

    @Test
    public void savesAndReReadsOrgStorageCredential() {
        OrgStorageCredential credential = new OrgStorageCredential();
        credential.setUuid(UUID.randomUUID().toString());
        credential.setCredentialRef("org-gcs-creds");
        credential.setAccessKey("ACCESS123");
        credential.setEncryptedSecretKey("base64-iv-prefixed-ciphertext");

        OrgStorageCredential saved = orgStorageCredentialRepository.save(credential);
        assertNotNull("save must assign an id (mapping ok)", saved.getId());
        assertNotNull("created_by_id must be mapped + populated", saved.getCreatedBy());
        assertNotNull("last_modified_by_id must be mapped + populated", saved.getLastModifiedBy());

        OrgStorageCredential reRead = orgStorageCredentialRepository.findByOrganisationIdAndCredentialRefAndIsVoidedFalse(
                saved.getOrganisationId(), "org-gcs-creds");
        assertNotNull("must re-read the saved credential", reRead);
        assertEquals("ACCESS123", reRead.getAccessKey());
        assertEquals("base64-iv-prefixed-ciphertext", reRead.getEncryptedSecretKey());
        assertEquals("org-gcs-creds", reRead.getCredentialRef());
    }

    @Test
    public void savesAndReReadsDownloadableContent() {
        DownloadableContent content = new DownloadableContent();
        content.setUuid(UUID.randomUUID().toString());
        content.setName("model-bundle-" + UUID.randomUUID());
        content.setCategory("MODEL");
        content.setContentKey("models/some-key");

        DownloadableContent saved = downloadableContentRepository.save(content);
        assertNotNull("save must assign an id (mapping ok)", saved.getId());
        assertNotNull(saved.getCreatedBy());
        assertNotNull(saved.getLastModifiedBy());

        DownloadableContent reRead = downloadableContentRepository.findEntity(saved.getId());
        assertNotNull(reRead);
        assertEquals("MODEL", reRead.getCategory());
        assertEquals("models/some-key", reRead.getContentKey());
    }
}

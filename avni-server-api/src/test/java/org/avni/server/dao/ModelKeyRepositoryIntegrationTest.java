package org.avni.server.dao;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.ModelKey;
import org.avni.server.service.builder.TestDataSetupService;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Transactional
public class ModelKeyRepositoryIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private TestDataSetupService testDataSetupService;
    @Autowired
    private ModelKeyRepository modelKeyRepository;

    @Before
    public void setUp() {
        testDataSetupService.setupOrganisation();
    }

    @Test
    public void savesAndReReadsModelKey() {
        ModelKey modelKey = new ModelKey();
        modelKey.setUuid(UUID.randomUUID().toString());
        modelKey.setSha256("sha256-of-plaintext-fold-1");
        modelKey.setEncryptedKey("base64-iv-prefixed-ciphertext");

        ModelKey saved = modelKeyRepository.save(modelKey);
        assertNotNull("save must assign an id (mapping ok)", saved.getId());
        assertNotNull("created_by_id must be mapped + populated", saved.getCreatedBy());
        assertNotNull("last_modified_by_id must be mapped + populated", saved.getLastModifiedBy());

        ModelKey reRead = modelKeyRepository.findByOrganisationIdAndSha256AndIsVoidedFalse(
                saved.getOrganisationId(), "sha256-of-plaintext-fold-1");
        assertNotNull("must re-read the saved key by (org, sha256)", reRead);
        assertEquals("sha256-of-plaintext-fold-1", reRead.getSha256());
        assertEquals("base64-iv-prefixed-ciphertext", reRead.getEncryptedKey());
    }

    @Test
    public void lookupByUnknownSha256ReturnsNull() {
        ModelKey modelKey = new ModelKey();
        modelKey.setUuid(UUID.randomUUID().toString());
        modelKey.setSha256("known-sha");
        modelKey.setEncryptedKey("base64-iv-prefixed-ciphertext");
        ModelKey saved = modelKeyRepository.save(modelKey);

        assertNull("an unknown sha256 must resolve to null (-> clean 404 in the endpoint)",
                modelKeyRepository.findByOrganisationIdAndSha256AndIsVoidedFalse(saved.getOrganisationId(), "no-such-sha"));
    }
}

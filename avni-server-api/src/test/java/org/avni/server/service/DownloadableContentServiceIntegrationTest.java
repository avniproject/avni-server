package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Two guidance pictures may not share a position and picture type: a rule finds a picture by that pair and the
 * phone takes the first match, so the second could show a stale picture. Checked against the real database, where
 * the stored position comes back through the JSON column. See avniproject/avni-webapp#1798.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class DownloadableContentServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private DownloadableContentService downloadableContentService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    // QA's case: two screens open, both saving a Reference photo for position 94.
    @Test
    public void aSecondPictureForTheSamePositionAndTypeIsRefused() {
        downloadableContentService.createOrUpdate(guidanceRequest("Left buccal mucosa - reference", 94, "reckoner", "a"));

        try {
            downloadableContentService.createOrUpdate(guidanceRequest("Left buccal mucosa - again", 94, "reckoner", "b"));
            fail("Expected BadRequestError for a second picture at the same position and type");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().contains("Left buccal mucosa - reference"));
            assertTrue(e.getMessage().contains("position 94"));
        }
    }

    @Test
    public void theOtherPictureTypeAndAVoidedPictureLeaveThePositionFree() {
        DownloadableContent reference = downloadableContentService.createOrUpdate(guidanceRequest("Reference", 94, "reckoner", "a"));
        downloadableContentService.createOrUpdate(guidanceRequest("Outline", 94, "overlay", "b"));
        downloadableContentService.deleteContent(reference.getUuid());

        DownloadableContent replacement = downloadableContentService.createOrUpdate(guidanceRequest("Replacement", 94, "reckoner", "c"));

        assertEquals(94, ((Number) replacement.getPayload().get("sequence")).intValue());
    }

    private DownloadableContentRequest guidanceRequest(String name, int sequence, String kind, String shaCharacter) {
        String sha256 = String.join("", Collections.nCopies(64, shaCharacter));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sequence", sequence);
        payload.put("site", "Left buccal mucosa");
        payload.put("kind", kind);
        DownloadableContentRequest request = new DownloadableContentRequest();
        request.setName(name);
        request.setCategory("guidanceImage");
        request.setContentKey("guidance/" + sha256 + ".png");
        request.setSha256(sha256);
        request.setNeedsKey(false);
        request.setPayload(payload);
        return request;
    }
}

package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.DownloadableContentRepository;
import org.avni.server.domain.DownloadableContent;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.DownloadableContentRequest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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
    @Autowired
    private DownloadableContentRepository downloadableContentRepository;

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

    // QA's case on 9 Oct, sent as QA sent it: with a Reference photo at position 91, a second one posted with the
    // position as the text "91" and the first record's checksum saved, and left two live pictures for 91.
    @Test
    public void aSecondPicturePostedWithItsPositionAsTextIsRefused() {
        ResponseEntity<String> first = postGuidance("QA1798 91 reckoner", "91");
        assertEquals(first.getBody(), HttpStatus.OK, first.getStatusCode());

        ResponseEntity<String> second = postGuidance("QA dup text position", "\"91\"");

        assertEquals(second.getBody(), HttpStatus.BAD_REQUEST, second.getStatusCode());
        assertTrue(second.getBody(), second.getBody().contains("position (a whole number, 1 or more)"));
        assertEquals(1, downloadableContentRepository.findAllByCategoryAndIsVoidedFalse("guidanceImage").size());
    }

    private ResponseEntity<String> postGuidance(String name, String sequenceJson) {
        String sha256 = String.join("", Collections.nCopies(64, "a"));
        String json = String.format("{\"uuid\":\"%s\",\"name\":\"%s\",\"category\":\"guidanceImage\",\"sha256\":\"%s\","
                        + "\"contentKey\":\"guidance/%s.png\",\"needsKey\":false,"
                        + "\"payload\":{\"sequence\":%s,\"site\":\"Test site\",\"kind\":\"reckoner\"}}",
                UUID.randomUUID(), name, sha256, sha256, sequenceJson);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return template.postForEntity(base + "web/downloadableContent", new HttpEntity<>(json, headers), String.class);
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

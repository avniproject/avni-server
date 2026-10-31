package org.avni.server.framework.sync;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Before;
import org.junit.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Over HTTP, so the page-size argument resolver is in the path (#1058).
 * Reads the size served (page.size in the response body), never the size sent.
 */
@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SyncPageSizeIntegrationTest extends AbstractControllerIntegrationTest {
    // A future lastModifiedDateTime keeps the page empty: page.size is still the size served, and nothing in the
    // shared test data has to serialise.
    static final String TRANSACTIONAL = "/individual/search/lastModified?subjectTypeUuid=9f2af1f9-e150-4f8e-aad3-40bb7eb05aa3&lastModifiedDateTime=2100-01-01T00:00:00.000Z&page=0&size=";
    static final String METADATA = "/formElementGroup/search/lastModified?lastModifiedDateTime=2100-01-01T00:00:00.000Z&page=0&size=";

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
    }

    static int servedSize(ResponseEntity<Map> response) {
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return ((Number) ((Map) response.getBody().get("page")).get("size")).intValue();
    }

    private ResponseEntity<Map> getMap(String url) {
        return template.getForEntity(base.toString() + url, Map.class);
    }

    private ResponseEntity<String> getString(String url) {
        return template.getForEntity(base.toString() + url, String.class);
    }

    @Test
    public void sizeAtTheMaximumIsServedAsAsked() {
        assertEquals(1000, servedSize(getMap(TRANSACTIONAL + "1000")));
        assertEquals(1000, servedSize(getMap(METADATA + "1000")));
    }

    @Test
    public void sizeAboveTheMaximumIsRefusedNotClamped_transactional() {
        assertRefused(getString(TRANSACTIONAL + "2000"), "2000");
    }

    @Test
    public void sizeAboveTheMaximumIsRefusedNotClamped_metadata() {
        assertRefused(getString(METADATA + "2000"), "2000");
    }

    @Test
    public void nonNumericSizeIsRefusedNotServedAsEmptyPage() {
        assertRefused(getString(TRANSACTIONAL + "abc"), "abc");
        assertRefused(getString(METADATA + "abc"), "abc");
    }

    private static void assertRefused(ResponseEntity<String> response, String asked) {
        assertEquals(String.valueOf(response.getBody()), HttpStatus.BAD_REQUEST, response.getStatusCode());
        String body = String.valueOf(response.getBody());
        assertTrue(body, body.contains(asked));
        assertTrue(body, body.contains("1000"));
    }
}

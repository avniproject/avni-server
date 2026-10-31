package org.avni.server.framework.sync;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.web.HateoasPageableHandlerMethodArgumentResolver;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.avni.server.framework.sync.SyncPageSizeIntegrationTest.METADATA;
import static org.avni.server.framework.sync.SyncPageSizeIntegrationTest.TRANSACTIONAL;
import static org.avni.server.framework.sync.SyncPageSizeIntegrationTest.servedSize;
import static org.junit.Assert.assertEquals;

/**
 * AVNI_SYNC_MAX_PAGE_SIZE raised. @TestPropertySource would force a second Spring context, which the application's
 * Ehcache CacheManager cannot survive in one JVM, so the two places the property lands are set on the shared beans
 * and put back afterwards.
 */
@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class SyncPageSizeRaisedMaximumIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    @Qualifier("pageableResolver")
    private HateoasPageableHandlerMethodArgumentResolver pageableResolver;
    @Autowired
    private PageSizeGuardInterceptor guard;
    private int original;

    private void setMaximum(int max) {
        pageableResolver.setMaxPageSize(max);
        ReflectionTestUtils.setField(guard, "maxPageSize", max);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-user");
        original = guard.getMaxPageSize();
        setMaximum(5000);
    }

    @After
    public void restore() {
        setMaximum(original);
    }

    @Test
    public void raisedMaximumServesTheLargerSizeOnBothFamilies() {
        assertEquals(2000, servedSize(template.getForEntity(base + TRANSACTIONAL + "2000", Map.class)));
        assertEquals(2000, servedSize(template.getForEntity(base + METADATA + "2000", Map.class)));
        assertEquals(HttpStatus.BAD_REQUEST, template.getForEntity(base + TRANSACTIONAL + "6000", String.class).getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, template.getForEntity(base + TRANSACTIONAL + "abc", String.class).getStatusCode());
    }
}

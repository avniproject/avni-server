package org.avni.server.service;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ArchivalConfigServiceIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ArchivalConfigService archivalConfigService;

    @Test
    public void validate() {
        assertNull(archivalConfigService.validateQuery("select id from individual where id = 1"));
        assertNull(archivalConfigService.validateQuery("select id from encounter where id = 1"));
        assertNull(archivalConfigService.validateQuery("select id from encounter where name = 'foo'"));
        assertEquals("Query should return one column but returned 2 columns.",
                archivalConfigService.validateQuery("select id, first_name from individual where id = 1"));
        assertEquals("Query should return a column of type (bigint, integer, serial) but returned: varchar.",
                archivalConfigService.validateQuery("select first_name from individual where id = 1"));
        assertEquals("Query should return a column of type (bigint, integer, serial) but returned: varchar.",
                archivalConfigService.validateQuery("select first_name id from individual where id = 1"));
        assertEquals("ERROR: cannot execute UPDATE in a read-only transaction",
                archivalConfigService.validateQuery("update encounter set name = 'foo' where id = 1"));
        assertEquals("ERROR: cannot execute DROP TABLE in a read-only transaction",
                archivalConfigService.validateQuery("select id from individual where id = 1; DROP TABLE concept;"));
    }
}

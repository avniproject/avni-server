package org.avni.server.dao.calendar;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User;
import org.avni.server.domain.calendar.Calendar;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.jdbc.Sql;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@Sql(value = {"/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
@Ignore("Requires test database from `make rebuild_testdb`; run as part of `make test_server`")
public class CalendarRepositoryUniqueIndexIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private CalendarRepository calendarRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void secondGlobalCalendarInSameOrgFailsUniqueIndex() {
        User user = userRepository.findByUsername("demo-admin");
        calendarRepository.save(buildGlobal("Global A", user));
        try {
            calendarRepository.save(buildGlobal("Global B", user));
            calendarRepository.flush();
            fail("Expected partial unique index to reject a second global calendar");
        } catch (DataIntegrityViolationException expected) {
        }
    }

    @Test
    public void voidedGlobalCalendarDoesNotBlockNewGlobalCalendar() {
        User user = userRepository.findByUsername("demo-admin");
        Calendar voided = buildGlobal("Global voided", user);
        voided.setVoided(true);
        calendarRepository.save(voided);
        calendarRepository.flush();

        Calendar active = calendarRepository.save(buildGlobal("Global active", user));
        calendarRepository.flush();
        assertEquals("Global active", active.getName());
    }

    private Calendar buildGlobal(String name, User user) {
        Calendar calendar = new Calendar();
        calendar.assignUUID();
        calendar.setName(name);
        calendar.setWorkingPattern(new JsonObject().with("mon", "all"));
        calendar.setAddressLevel(null);
        calendar.setOrganisationId(user.getOrganisationId());
        calendar.setCreatedBy(user);
        calendar.setLastModifiedBy(user);
        calendar.setCreatedDateTime(org.joda.time.DateTime.now());
        calendar.setLastModifiedDateTime(org.joda.time.DateTime.now());
        return calendar;
    }
}

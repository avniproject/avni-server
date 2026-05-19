package org.avni.server.web.calendar;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.accessControl.AccessControlService;
import org.avni.server.service.calendar.CalendarService;
import org.avni.server.web.request.calendar.CalendarContract;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CalendarControllerTest {

    @Mock
    private CalendarService calendarService;
    @Mock
    private CalendarRepository calendarRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private AccessControlService accessControlService;

    private CalendarController controller;

    @Before
    public void setUp() {
        initMocks(this);
        controller = new CalendarController(calendarService, calendarRepository, locationRepository, accessControlService);
        UserContext ctx = new UserContext();
        User user = new User();
        user.setUuid("user-uuid");
        ctx.setUser(user);
        UserContextHolder.create(ctx);
        when(calendarService.save(any(Calendar.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @After
    public void tearDown() {
        UserContextHolder.clear();
    }

    @Test
    public void createWithNullWorkingPatternAppliesDefaultPattern() {
        CalendarContract contract = new CalendarContract();
        contract.setName("Org Calendar");
        contract.setWorkingPattern(null);

        ResponseEntity<CalendarContract> response = controller.create(contract);

        ArgumentCaptor<Calendar> captor = ArgumentCaptor.forClass(Calendar.class);
        verify(calendarService).save(captor.capture());
        JsonObject saved = captor.getValue().getWorkingPattern();
        assertNotNull(saved);
        assertEquals("all", saved.get("mon"));
        assertEquals("all", saved.get("tue"));
        assertEquals("all", saved.get("wed"));
        assertEquals("all", saved.get("thu"));
        assertEquals("all", saved.get("fri"));
        assertEquals("none", saved.get("sat"));
        assertEquals("none", saved.get("sun"));
        assertNotNull(response.getBody());
    }

    @Test
    public void createWithProvidedWorkingPatternUsesIt() {
        Map<String, Object> pattern = new LinkedHashMap<>();
        pattern.put("mon", "morning");
        pattern.put("tue", "afternoon");
        CalendarContract contract = new CalendarContract();
        contract.setName("Custom");
        contract.setWorkingPattern(pattern);

        controller.create(contract);

        ArgumentCaptor<Calendar> captor = ArgumentCaptor.forClass(Calendar.class);
        verify(calendarService).save(captor.capture());
        JsonObject saved = captor.getValue().getWorkingPattern();
        assertEquals("morning", saved.get("mon"));
        assertEquals("afternoon", saved.get("tue"));
        assertEquals(2, saved.size());
    }
}

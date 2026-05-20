package org.avni.server.service.calendar;

import org.avni.server.dao.LocationRepository;
import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.calendar.CalendarContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class CalendarServiceTest {

    @Mock
    private CalendarRepository calendarRepository;
    @Mock
    private CalendarDateMarkerRepository calendarDateMarkerRepository;
    @Mock
    private LocationRepository locationRepository;

    private CalendarService service;

    @Before
    public void setUp() {
        initMocks(this);
        service = new CalendarService(calendarRepository, calendarDateMarkerRepository, locationRepository);
        when(calendarRepository.save(any(Calendar.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    public void firstCalendarInOrgAutoMarkedDefault() {
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(Collections.emptyList());

        Calendar calendar = newCalendar(null);
        Calendar saved = service.save(calendar);

        assertTrue(saved.isDefault());
    }

    @Test
    public void rejectsSecondGlobalWhenPerLocationExists() {
        AddressLevel addressLevel = new AddressLevel();
        addressLevel.setId(7L);
        Calendar existing = newCalendar(addressLevel);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(List.of(existing));

        Calendar candidate = newCalendar(null);
        try {
            service.save(candidate);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("per-location"));
        }
    }

    @Test
    public void rejectsPerLocationWhenGlobalExists() {
        Calendar existingGlobal = newCalendar(null);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(List.of(existingGlobal));

        AddressLevel addressLevel = new AddressLevel();
        addressLevel.setId(7L);
        Calendar candidate = newCalendar(addressLevel);
        try {
            service.save(candidate);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("global"));
        }
    }

    @Test
    public void rejectsDuplicateCalendarAtSameAddressLevel() {
        AddressLevel level = new AddressLevel();
        level.setId(7L);
        Calendar existing = newCalendar(level);
        existing.setId(1L);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(List.of(existing));

        Calendar candidate = newCalendar(level);
        try {
            service.save(candidate);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("address level"));
        }
    }

    @Test
    public void deleteCascadesToDateMarkers() {
        Calendar target = newCalendar(null);
        target.setId(1L);
        target.setDefault(false);
        Calendar other = newCalendar(null);
        other.setId(2L);
        CalendarDateMarker marker = new CalendarDateMarker();
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(Arrays.asList(target, other));
        when(calendarDateMarkerRepository.findByCalendarAndIsVoidedFalse(target)).thenReturn(List.of(marker));
        when(calendarDateMarkerRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(target);

        assertTrue(target.isVoided());
        assertTrue(marker.isVoided());
        verify(calendarDateMarkerRepository).saveAll(anyList());
    }

    @Test
    public void deleteBlockedWhenDefaultAndOnly() {
        Calendar target = newCalendar(null);
        target.setId(1L);
        target.setDefault(true);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(List.of(target));

        try {
            service.delete(target);
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("only"));
        }
        assertFalse(target.isVoided());
        verify(calendarDateMarkerRepository, never()).saveAll(anyList());
    }

    @Test
    public void deletePromotesAnotherDefaultWhenAvailable() {
        Calendar target = newCalendar(null);
        target.setId(1L);
        target.setDefault(true);
        Calendar nextDefault = newCalendar(null);
        nextDefault.setId(2L);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(Arrays.asList(target, nextDefault));
        when(calendarDateMarkerRepository.findByCalendarAndIsVoidedFalse(target)).thenReturn(Collections.emptyList());

        service.delete(target);

        assertTrue(target.isVoided());
        assertTrue(nextDefault.isDefault());
        verify(calendarRepository, times(2)).save(any(Calendar.class));
    }

    @Test
    public void saveFromBundleRejectsRowThatViolatesModeInvariant() {
        AddressLevel level = new AddressLevel();
        level.setId(7L);
        Calendar existingPerLocation = newCalendar(level);
        existingPerLocation.setId(1L);
        when(calendarRepository.findByUuid("bundle-global-uuid")).thenReturn(null);
        when(calendarRepository.findAllByIsVoidedFalse()).thenReturn(List.of(existingPerLocation));

        CalendarContract globalContract = new CalendarContract();
        globalContract.setUuid("bundle-global-uuid");
        globalContract.setName("Global from bundle");
        globalContract.setAddressLevelUUID(null);

        try {
            service.saveFromBundle(new CalendarContract[]{globalContract});
            fail("Expected BadRequestError");
        } catch (BadRequestError e) {
            assertTrue(e.getMessage().toLowerCase().contains("per-location"));
        }
    }

    private Calendar newCalendar(AddressLevel addressLevel) {
        Calendar calendar = new Calendar();
        calendar.setUuid(java.util.UUID.randomUUID().toString());
        calendar.setName("Test Calendar");
        calendar.setAddressLevel(addressLevel);
        return calendar;
    }
}

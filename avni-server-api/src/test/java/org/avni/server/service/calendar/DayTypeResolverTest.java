package org.avni.server.service.calendar;

import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.domain.calendar.DayType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class DayTypeResolverTest {

    @Mock
    private CalendarDateMarkerRepository markerRepository;

    private static final LocalDate MONDAY = LocalDate.of(2026, 5, 18);
    private static final LocalDate SATURDAY = LocalDate.of(2026, 5, 23);

    @Before
    public void setUp() {
        initMocks(this);
    }

    @Test
    public void nullCalendarResolvesToWorkingDay() {
        assertEquals(DayType.working_day, DayTypeResolver.resolve(null, MONDAY, markerRepository));
    }

    @Test
    public void markerWithIsWorkingTrueResolvesToWorkingOverride() {
        Calendar calendar = calendarWithPattern(allWeek());
        CalendarDateMarker marker = new CalendarDateMarker();
        marker.setWorking(true);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(calendar, SATURDAY)).thenReturn(marker);

        assertEquals(DayType.working_override, DayTypeResolver.resolve(calendar, SATURDAY, markerRepository));
    }

    @Test
    public void markerWithIsWorkingFalseResolvesToPublicHoliday() {
        Calendar calendar = calendarWithPattern(allWeek());
        CalendarDateMarker marker = new CalendarDateMarker();
        marker.setWorking(false);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(calendar, MONDAY)).thenReturn(marker);

        assertEquals(DayType.public_holiday, DayTypeResolver.resolve(calendar, MONDAY, markerRepository));
    }

    @Test
    public void allWeekdayResolvesToWorkingDay() {
        Calendar calendar = calendarWithPattern(allWeek());
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.working_day, DayTypeResolver.resolve(calendar, MONDAY, markerRepository));
    }

    @Test
    public void noneWeekdayResolvesToWeeklyOff() {
        JsonObject pattern = allWeek().with("sat", "none");
        Calendar calendar = calendarWithPattern(pattern);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.weekly_off, DayTypeResolver.resolve(calendar, SATURDAY, markerRepository));
    }

    @Test
    public void listedShiftResolvesToWorkingDay() {
        // MONDAY is the 3rd Monday of the month, and 3 is ticked.
        JsonObject pattern = allWeek().with("mon", List.of(1, 3, 5));
        Calendar calendar = calendarWithPattern(pattern);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.working_day, DayTypeResolver.resolve(calendar, MONDAY, markerRepository));
    }

    @Test
    public void unlistedShiftResolvesToWeeklyOff() {
        // MONDAY is the 3rd Monday of the month, and only the 1st and 2nd are ticked.
        JsonObject pattern = allWeek().with("mon", List.of(1, 2));
        Calendar calendar = calendarWithPattern(pattern);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.weekly_off, DayTypeResolver.resolve(calendar, MONDAY, markerRepository));
    }

    @Test
    public void alternateSaturdayPatternResolvesPerOccurrence() {
        // The Bengaluru-schools pattern from the feature spec: 1st, 3rd and 5th Saturdays work.
        JsonObject pattern = allWeek().with("sat", List.of(1, 3, 5));
        Calendar calendar = calendarWithPattern(pattern);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.working_day, DayTypeResolver.resolve(calendar, LocalDate.of(2026, 5, 2), markerRepository));
        assertEquals(DayType.weekly_off, DayTypeResolver.resolve(calendar, LocalDate.of(2026, 5, 9), markerRepository));
        assertEquals(DayType.working_day, DayTypeResolver.resolve(calendar, LocalDate.of(2026, 5, 16), markerRepository));
        assertEquals(DayType.weekly_off, DayTypeResolver.resolve(calendar, SATURDAY, markerRepository));
        assertEquals(DayType.working_day, DayTypeResolver.resolve(calendar, LocalDate.of(2026, 5, 30), markerRepository));
    }

    @Test
    public void emptyShiftArrayResolvesToWeeklyOff() {
        JsonObject pattern = allWeek().with("mon", List.of());
        Calendar calendar = calendarWithPattern(pattern);
        when(markerRepository.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(any(), any())).thenReturn(null);

        assertEquals(DayType.weekly_off, DayTypeResolver.resolve(calendar, MONDAY, markerRepository));
    }

    private Calendar calendarWithPattern(JsonObject pattern) {
        Calendar calendar = new Calendar();
        calendar.setUuid("cal-uuid");
        calendar.setWorkingPattern(pattern);
        return calendar;
    }

    private JsonObject allWeek() {
        return new JsonObject()
                .with("mon", "all").with("tue", "all").with("wed", "all").with("thu", "all")
                .with("fri", "all").with("sat", "all").with("sun", "all");
    }
}

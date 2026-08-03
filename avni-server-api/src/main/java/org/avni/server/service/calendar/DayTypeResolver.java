package org.avni.server.service.calendar;

import org.avni.server.dao.calendar.CalendarDateMarkerRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.calendar.Calendar;
import org.avni.server.domain.calendar.CalendarDateMarker;
import org.avni.server.domain.calendar.DayType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

public final class DayTypeResolver {

    private static final String[] DAY_KEYS = {"mon", "tue", "wed", "thu", "fri", "sat", "sun"};

    private DayTypeResolver() {
    }

    public static DayType resolve(Calendar calendar, LocalDate date, CalendarDateMarkerRepository markerRepo) {
        if (calendar == null || date == null) {
            return DayType.working_day;
        }
        CalendarDateMarker marker = markerRepo.findFirstByCalendarAndMarkerDateAndIsVoidedFalse(calendar, date);
        if (marker != null) {
            return marker.isWorking() ? DayType.working_override : DayType.public_holiday;
        }
        return resolveFromPattern(calendar.getWorkingPattern(), date);
    }

    static DayType resolveFromPattern(JsonObject workingPattern, LocalDate date) {
        if (workingPattern == null) {
            return DayType.working_day;
        }
        String key = DAY_KEYS[indexOf(date.getDayOfWeek())];
        Object value = workingPattern.get(key);
        if (value instanceof String) {
            String s = (String) value;
            if ("all".equals(s)) return DayType.working_day;
            if ("none".equals(s)) return DayType.weekly_off;
        }
        if (value instanceof List) {
            return isWorkingOccurrence((List<?>) value, occurrenceOf(date)) ? DayType.working_day : DayType.weekly_off;
        }
        return DayType.working_day;
    }

    private static int indexOf(DayOfWeek dow) {
        return dow.getValue() - 1;
    }

    // A working_pattern array holds week-of-month occurrences (ints 1-5, enforced by
    // CalendarWorkingPatternValidator): "sat": [1,3,5] means the 1st, 3rd and 5th Saturday are
    // working days and the 2nd and 4th are weekly offs. Occurrences of a given weekday are seven
    // days apart, so the nth one always falls on days 7(n-1)+1 through 7n.
    private static int occurrenceOf(LocalDate date) {
        return ((date.getDayOfMonth() - 1) / 7) + 1;
    }

    private static boolean isWorkingOccurrence(List<?> occurrences, int occurrence) {
        for (Object element : occurrences) {
            if (element instanceof Number && ((Number) element).intValue() == occurrence) {
                return true;
            }
        }
        return false;
    }
}

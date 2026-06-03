package org.avni.server.service.calendar;

import org.avni.server.dao.calendar.CalendarRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.Individual;
import org.avni.server.domain.calendar.Calendar;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class CalendarsResolver {

    private CalendarsResolver() {
    }

    public static Calendar forSubject(Individual subject, CalendarRepository calendarRepository) {
        List<Calendar> calendars = calendarRepository.findAllByIsVoidedFalse();
        if (calendars.isEmpty()) {
            return null;
        }
        if (subject != null && subject.getAddressLevel() != null) {
            Calendar perLocation = matchPerLocation(subject.getAddressLevel(), calendars);
            if (perLocation != null) {
                return perLocation;
            }
        }
        return pickGlobal(calendars);
    }

    private static Calendar matchPerLocation(AddressLevel addressLevel, List<Calendar> calendars) {
        Map<Long, Calendar> byAddressLevelId = calendars.stream()
                .filter(c -> c.getAddressLevel() != null)
                .collect(Collectors.toMap(c -> c.getAddressLevel().getId(), c -> c, (a, b) -> a));
        if (byAddressLevelId.isEmpty()) {
            return null;
        }
        // getLineageAddressIds() returns [root, ..., leaf]; reverse so the nearest ancestor wins.
        List<Long> lineage = addressLevel.getLineageAddressIds();
        Collections.reverse(lineage);
        for (Long id : lineage) {
            Calendar match = byAddressLevelId.get(id);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static Calendar pickGlobal(List<Calendar> calendars) {
        return calendars.stream()
                .filter(c -> c.getAddressLevel() == null)
                .sorted(Comparator
                        .comparing(Calendar::isDefault).reversed()
                        .thenComparing(Calendar::getId))
                .findFirst()
                .orElse(null);
    }
}

package org.avni.server.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalDate;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.TimeZone;

public class DateTimeUtil {

    public static final DateTimeZone IST = DateTimeZone.forOffsetHoursMinutes(5, 30);

    public static Calendar getCalendarTime(DateTime dateTime, String timeZone) {
        if (dateTime == null) return null;
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        calendar.setTime(dateTime.toDate());
        return calendar;
    }

    public static DateTime getDateForTimeZone(DateTime dateTime, String timeZone) {
        return dateTime == null ? null : dateTime.withZone(DateTimeZone.forID(timeZone));
    }

    public static DateTime parseNullableDateTime(String dateTimeString) {
        if (dateTimeString == null) {
            return null;
        }
        return DateTime.parse(dateTimeString);
    }

    public static LocalDate parseNullableDate(Object localDateString) {
        if (localDateString == null) {
            return null;
        }
        return LocalDate.parse((String) localDateString);
    }

    public static String toDateString(String dateStringWithTimezone) {
        return Instant.parse(dateStringWithTimezone)
                .toDateTime(IST)
                .toString("yyyy-MM-dd");
    }

    public static long getMilliSecondsDuration(LocalDateTime start, LocalDateTime end) {
        return java.time.Duration.between(start, end).toMillis();
    }
}

package org.avni.server.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;

import java.util.Calendar;
import java.util.TimeZone;

public class DateTimeUtil {
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
}

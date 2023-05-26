package org.avni.server.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

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
}

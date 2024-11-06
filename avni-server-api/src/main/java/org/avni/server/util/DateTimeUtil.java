package org.avni.server.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.LocalDate;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
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

    public static LocalDate toJodaDate(ZonedDateTime zonedDateTime) {
        return DateTimeUtil.toJodaDateTime(zonedDateTime).toLocalDate();
    }

    public static LocalDate toJodaDate(java.time.Instant instant) {
        return DateTimeUtil.toJodaDateTime(instant).toLocalDate();
    }

    public static java.time.Instant toInstant(LocalDate localDate) {
        if (localDate == null) {
            return null;
        }
        return localDate.toDate().toInstant();
    }

    public static DateTime toJodaDateTime(ZonedDateTime zonedDateTime) {
        return new DateTime(
                zonedDateTime.toInstant().toEpochMilli(),
                DateTimeZone.forTimeZone(TimeZone.getTimeZone(zonedDateTime.getZone())));
    }

    public static java.time.Instant toInstant(DateTime createdDateTime) {
        if (createdDateTime == null) {
            return null;
        }
        return createdDateTime.toDate().toInstant();
    }

    public static DateTime toJodaDateTime(java.time.Instant instant) {
        if (instant == null) {
            return null;
        }
        return new DateTime(instant.toEpochMilli());
    }

    public static java.time.Instant toInstant(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant();
    }

    public static java.time.Instant toInstant(Calendar calendar) {
        return calendar == null ? null : calendar.toInstant();
    }
}

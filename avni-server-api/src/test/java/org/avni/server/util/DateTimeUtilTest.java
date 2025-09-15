package org.avni.server.util;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNull;

public class DateTimeUtilTest {
    @Test
    public void shouldGetTheRightDateForDateTimeString() {
        String aDateString = "2020-01-31T18:30:00.000+00:00";
        assertThat(DateTimeUtil.toDateString(aDateString)).isEqualTo("2020-02-01");
    }

    @Test
    public void parseFlexibleDate() {
        LocalDate date1 = DateTimeUtil.parseFlexibleDate("2022-01-31");
        LocalDate date2 = DateTimeUtil.parseFlexibleDate("10-12-2023");

        assertThat(date1.getYear()).isEqualTo(2022);
        assertThat(date1.getMonthOfYear()).isEqualTo(1);
        assertThat(date1.getDayOfMonth()).isEqualTo(31);

        assertThat(date2.getYear()).isEqualTo(2023);
        assertThat(date2.getMonthOfYear()).isEqualTo(12);
        assertThat(date2.getDayOfMonth()).isEqualTo(10);
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFlexibleDateFailsForUnsupportedDate() {
        DateTimeUtil.parseFlexibleDate("2023-13-12");
    }

    @Test
    public void testGetCalendarTime() {
        // Test with a specific DateTime
        DateTime dateTime = new DateTime(2023, 5, 15, 10, 30, 0, DateTimeZone.UTC);
        Calendar calendar = DateTimeUtil.getCalendarTime(dateTime, "UTC");

        assertThat(calendar).isNotNull();
        assertThat(calendar.get(Calendar.YEAR)).isEqualTo(2023);
        assertThat(calendar.get(Calendar.MONTH)).isEqualTo(Calendar.MAY);
        assertThat(calendar.get(Calendar.DAY_OF_MONTH)).isEqualTo(15);
        assertThat(calendar.get(Calendar.HOUR_OF_DAY)).isEqualTo(10);
        assertThat(calendar.get(Calendar.MINUTE)).isEqualTo(30);
        assertThat(calendar.getTimeZone()).isEqualTo(TimeZone.getTimeZone("UTC"));

        // Test with null DateTime
        Calendar nullCalendar = DateTimeUtil.getCalendarTime(null, "UTC");
        assertNull(nullCalendar);
    }

    @Test
    public void testGetDateForTimeZone() {
        // Test with a specific DateTime
        DateTime utcDateTime = new DateTime(2023, 5, 15, 10, 30, 0, DateTimeZone.UTC);
        DateTime istDateTime = DateTimeUtil.getDateForTimeZone(utcDateTime, "Asia/Kolkata");

        assertThat(istDateTime).isNotNull();
        assertThat(istDateTime.getZone().getID()).isEqualTo("Asia/Kolkata");
        // UTC+5:30 for IST, so 10:30 UTC should be 16:00 IST
        assertThat(istDateTime.getHourOfDay()).isEqualTo(16);
        assertThat(istDateTime.getMinuteOfHour()).isEqualTo(0);

        // Test with null DateTime
        DateTime nullDateTime = DateTimeUtil.getDateForTimeZone(null, "Asia/Kolkata");
        assertNull(nullDateTime);
    }

    @Test
    public void testParseNullableDateTime() {
        // Test with valid DateTime string
        String validDateTimeString = "2023-05-15T10:30:00.000Z";
        DateTime dateTime = DateTimeUtil.parseNullableDateTime(validDateTimeString);

        assertThat(dateTime).isNotNull();
        assertThat(dateTime.getYear()).isEqualTo(2023);
        assertThat(dateTime.getMonthOfYear()).isEqualTo(5);
        assertThat(dateTime.getDayOfMonth()).isEqualTo(15);
        assertThat(dateTime.getHourOfDay()).isEqualTo(10);
        assertThat(dateTime.getMinuteOfHour()).isEqualTo(30);

        // Test with null input
        DateTime nullDateTime = DateTimeUtil.parseNullableDateTime(null);
        assertNull(nullDateTime);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullableDateTimeWithInvalidFormat() {
        // Test with invalid DateTime string
        DateTimeUtil.parseNullableDateTime("invalid-date-format");
    }

    @Test
    public void testParseNullableDate() {
        // Test with valid date string
        String validDateString = "2023-05-15";
        LocalDate date = DateTimeUtil.parseNullableDate(validDateString);

        assertThat(date).isNotNull();
        assertThat(date.getYear()).isEqualTo(2023);
        assertThat(date.getMonthOfYear()).isEqualTo(5);
        assertThat(date.getDayOfMonth()).isEqualTo(15);

        // Test with null input
        LocalDate nullDate = DateTimeUtil.parseNullableDate(null);
        assertNull(nullDate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseNullableDateWithInvalidFormat() {
        // Test with invalid date string
        DateTimeUtil.parseNullableDate("invalid-date-format");
    }

    @Test
    public void testGetMilliSecondsDuration() {
        // Test with specific start and end times
        LocalDateTime start = LocalDateTime.of(2023, 5, 15, 10, 30, 0);
        LocalDateTime end = LocalDateTime.of(2023, 5, 15, 10, 30, 30); // 30 seconds later

        long duration = DateTimeUtil.getMilliSecondsDuration(start, end);
        assertThat(duration).isEqualTo(30000); // 30 seconds = 30,000 milliseconds

        // Test with same start and end time
        duration = DateTimeUtil.getMilliSecondsDuration(start, start);
        assertThat(duration).isEqualTo(0);

        // Test with end time before start time (negative duration)
        duration = DateTimeUtil.getMilliSecondsDuration(end, start);
        assertThat(duration).isEqualTo(-30000); // -30 seconds = -30,000 milliseconds
    }
}

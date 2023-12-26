package org.avni.server.util;

import org.joda.time.DateTimeZone;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTimeUtilTest {

    @Test
    public void shouldGetTheRightDateForDateTimeString() {
        String aDateString = "2020-01-31T18:30:00.000+00:00";

        DateTimeZone.setDefault(DateTimeZone.forOffsetHoursMinutes(5, 30));
        assertThat(DateTimeUtil.toDateString(aDateString)).isEqualTo("2020-02-01");

        DateTimeZone.setDefault(DateTimeZone.UTC);
        assertThat(DateTimeUtil.toDateString(aDateString)).isEqualTo("2020-01-31");
    }
}

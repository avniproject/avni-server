package org.avni.server.util;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DateTimeUtilTest {
    @Test
    public void shouldGetTheRightDateForDateTimeString() {
        String aDateString = "2020-01-31T18:30:00.000+00:00";
        assertThat(DateTimeUtil.toDateString(aDateString)).isEqualTo("2020-02-01");
    }

    @Test
    public void parseFlexibleDate() {
        DateTimeUtil.parseFlexibleDate("2022-01-31");
        DateTimeUtil.parseFlexibleDate("10-12-2023");
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseFlexibleDateFailsForUnsupportedDate() {
        DateTimeUtil.parseFlexibleDate("2023-13-12");
    }
}

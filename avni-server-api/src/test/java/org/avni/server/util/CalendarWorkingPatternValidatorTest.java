package org.avni.server.util;

import org.avni.server.domain.JsonObject;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class CalendarWorkingPatternValidatorTest {

    @Test
    public void acceptsAllAndNoneStrings() {
        CalendarWorkingPatternValidator.validate(allWeek());
        CalendarWorkingPatternValidator.validate(allWeek().with("sat", "none").with("sun", "none"));
    }

    @Test
    public void acceptsShiftArrays() {
        CalendarWorkingPatternValidator.validate(allWeek().with("sat", List.of(1, 3, 5)));
        CalendarWorkingPatternValidator.validate(allWeek().with("sat", List.of()));
        CalendarWorkingPatternValidator.validate(allWeek().with("sat", List.of(1, 2, 3, 4, 5)));
    }

    @Test
    public void rejectsFractionalShift() {
        // 2.5 would previously truncate to 2 and be stored verbatim, but avni-etl's jsonb containment
        // and the client's _.includes both match exactly, so it would resolve as no-match everywhere.
        assertRejected(allWeek().with("sat", List.of(2.5)), "whole numbers");
    }

    @Test
    public void rejectsShiftOutsideOneToFive() {
        assertRejected(allWeek().with("sat", List.of(0)), "1-5");
        assertRejected(allWeek().with("sat", List.of(6)), "1-5");
    }

    @Test
    public void rejectsDuplicateShift() {
        assertRejected(allWeek().with("sat", List.of(1, 1)), "duplicate");
    }

    @Test
    public void rejectsNonNumericShift() {
        assertRejected(allWeek().with("sat", List.of("1")), "numeric");
    }

    @Test
    public void rejectsUnknownStringValue() {
        assertRejected(allWeek().with("sat", "sometimes"), "'all' or 'none'");
    }

    @Test
    public void rejectsMissingOrExtraDayKeys() {
        JsonObject missingSunday = new JsonObject()
                .with("mon", "all").with("tue", "all").with("wed", "all").with("thu", "all")
                .with("fri", "all").with("sat", "all");
        assertRejected(missingSunday, "exactly the keys");
        assertRejected(allWeek().with("funday", "all"), "exactly the keys");
    }

    @Test
    public void rejectsNullPattern() {
        assertRejected(null, "required");
    }

    private void assertRejected(JsonObject pattern, String expectedInMessage) {
        try {
            CalendarWorkingPatternValidator.validate(pattern);
            fail("Expected BadRequestError containing: " + expectedInMessage);
        } catch (BadRequestError e) {
            assertTrue("Expected message to contain '" + expectedInMessage + "' but was: " + e.getMessage(),
                    e.getMessage().contains(expectedInMessage));
        }
    }

    private JsonObject allWeek() {
        return new JsonObject()
                .with("mon", "all").with("tue", "all").with("wed", "all").with("thu", "all")
                .with("fri", "all").with("sat", "all").with("sun", "all");
    }
}

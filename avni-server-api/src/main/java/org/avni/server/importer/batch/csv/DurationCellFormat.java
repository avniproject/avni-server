package org.avni.server.importer.batch.csv;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.avni.messaging.domain.Constants.DURATION_PATTERN;

// How a Duration cell of an upload sheet is read, and how the sample file and the row error describe it
// (avniproject/avni-server#959). The app reads a duration only in the ISO form it writes, such as PT35M,
// and shows on edit only the units that were stored. So a value is stored only if it passes the same
// check as a save from the app and uses no unit the question does not take.
public final class DurationCellFormat {
    private static final Pattern ISO_DURATION = Pattern.compile(DURATION_PATTERN);
    private static final String ANY_UNIT_EXAMPLE = "a duration such as PT30M (30 minutes) or P1Y6M (1 year 6 months)";

    // In the order DURATION_PATTERN takes them. group is the unit's capturing group in that pattern.
    private enum Unit {
        years(1, "Y", false, 2), months(2, "M", false, 6), weeks(3, "W", false, 3), days(4, "D", false, 10),
        hours(6, "H", true, 2), minutes(7, "M", true, 30), seconds(8, "S", true, 15);

        private final int group;
        private final String letter;
        private final boolean time;
        private final int exampleValue;

        Unit(int group, String letter, boolean time, int exampleValue) {
            this.group = group;
            this.letter = letter;
            this.time = time;
            this.exampleValue = exampleValue;
        }
    }

    private DurationCellFormat() {
    }

    /**
     * @param questionUnits the units set on the question, as FormElement.getDurationOptions() returns them
     * @return the value to store, or null when the cell cannot be stored as a duration for this question
     */
    public static String toStoredValue(String cell, List<String> questionUnits) {
        String trimmed = cell.trim();
        List<Unit> units = known(questionUnits);
        // A plain whole number is what a person types under a heading such as "in minutes".
        if (units.size() == 1 && trimmed.matches("\\d{1,9}")) {
            return toIso(List.of(units.get(0)), Integer.parseInt(trimmed));
        }
        Matcher matcher = ISO_DURATION.matcher(trimmed);
        if (!matcher.matches()) {
            return null;
        }
        boolean usesAUnitTheQuestionDoesNotTake = !units.isEmpty() && Arrays.stream(Unit.values())
                .anyMatch(unit -> matcher.group(unit.group) != null && !units.contains(unit));
        return usesAUnitTheQuestionDoesNotTake ? null : trimmed;
    }

    /** What to type in the cell, with an example in the question's own units. Completes "Enter ..." and "Format: ...". */
    public static String whatToEnter(List<String> questionUnits) {
        List<Unit> units = known(questionUnits);
        if (units.isEmpty()) {
            return ANY_UNIT_EXAMPLE;
        }
        String inWords = units.stream().map(unit -> unit.exampleValue + " " + unit.name()).collect(Collectors.joining(" "));
        String example = String.format("a duration such as %s (%s)", toIso(units, null), inWords);
        return units.size() == 1 ? String.format("a whole number of %s, or %s", units.get(0).name(), example) : example;
    }

    // value == null writes each unit's example value.
    private static String toIso(List<Unit> units, Integer value) {
        StringBuilder iso = new StringBuilder("P");
        boolean inTime = false;
        for (Unit unit : units) {
            if (unit.time && !inTime) {
                iso.append("T");
                inTime = true;
            }
            iso.append(value == null ? unit.exampleValue : value).append(unit.letter);
        }
        return iso.toString();
    }

    // The question's units that this class knows, in ISO order whatever order the question lists them in.
    private static List<Unit> known(List<String> questionUnits) {
        return Arrays.stream(Unit.values()).filter(unit -> questionUnits.contains(unit.name())).toList();
    }
}

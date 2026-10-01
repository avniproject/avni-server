package org.avni.server.util;

/**
 * One CSV cell, quoted so that what is inside it cannot be mistaken for the structure around it.
 * <p>
 * Exports are assembled by hand rather than through a CSV library, and the parts being assembled
 * are names people typed: a subject, a village, a question, a subject type, an error message. A
 * comma in one of those splits the row, and a quote closes the cell early and shifts every column
 * after it. Both had to be got right in four separate places, so they live here once.
 */
public class CsvCell {
    /**
     * Always quoted, with any quote inside doubled.
     */
    public static String quoted(String value) {
        return "\"" + escape(value) + "\"";
    }

    /**
     * Quoted when it has to be. Pass {@code alwaysQuote} for a cell whose existing output is
     * already quoted, so this does not change what a reader has been getting. Otherwise the cell
     * is left bare unless it holds a comma or a quote, which is the case that was breaking rows.
     */
    public static String quoteIfNeeded(String value, boolean alwaysQuote) {
        String content = value == null ? "" : value;
        boolean mustQuote = alwaysQuote || content.indexOf(',') >= 0 || content.indexOf('"') >= 0 || content.indexOf('\n') >= 0;
        return mustQuote ? quoted(content) : content;
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\"", "\"\"");
    }
}

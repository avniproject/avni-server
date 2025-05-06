package org.avni.server.importer.batch.csv.writer.header;

import java.util.ArrayList;
import java.util.List;

public class HeaderField {
    private final String header;
    private final String description;
    private final boolean mandatory;
    private final String allowedValues;
    private final String format;
    private final String editable;
    private final Boolean shouldShowIfMandatory;

    public HeaderField(String header, String description, boolean mandatory, String allowedValues, String format, String editable, Boolean shouldShowIfMandatory) {
        this.header = header;
        this.description = description;
        this.mandatory = mandatory;
        this.allowedValues = allowedValues;
        this.format = format;
        this.editable = editable;
        this.shouldShowIfMandatory = shouldShowIfMandatory;
    }

    public HeaderField(String header, String description, boolean mandatory, String allowedValues, String format, String editable) {
        this(header, description, mandatory, allowedValues, format, editable, true);
    }

    public String getHeader() {
        return header;
    }

    public boolean isMandatory() {
        return mandatory;
    }

    private String formatForCsv(String input) {
        if (input == null) {
            return "";
        }

        String cleaned = input.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }

        if (cleaned.contains(",") || cleaned.contains("\"") || cleaned.contains("\n")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    public String getDescription() {
        List<String> parts = new ArrayList<>();

        if (shouldShowIfMandatory && mandatory) {
            parts.add("Mandatory");
        } else if (!mandatory) {
            parts.add("Optional");
        }

        if (!description.isEmpty()) {
            parts.add(description);
        }
        if (allowedValues != null) {
            parts.add(allowedValues);
        }
        if (format != null) {
            parts.add(format);
        }
        if (editable != null) {
            parts.add(editable);
        }

        if (parts.isEmpty()) {
            return "";
        } else if (parts.size() == 1) {
            return formatForCsv(parts.getFirst());
        } else {
            return formatForCsv(String.join(". ", parts) + ".");
        }
    }

}
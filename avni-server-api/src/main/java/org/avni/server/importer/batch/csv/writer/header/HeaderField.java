package org.avni.server.importer.batch.csv.writer.header;

public class HeaderField {
    private final String header;
    private final String description;
    private final boolean mandatory;
    private final String allowedValues;
    private final String format;
    private final String editable;

    public HeaderField(String header, String description, boolean mandatory, String allowedValues, String format, String editable) {
        this.header = header;
        this.description = description;
        this.mandatory = mandatory;
        this.allowedValues = allowedValues;
        this.format = format;
        this.editable = editable;
    }

    public String getHeader() {
        return header;
    }

    public String getDescription() {
        StringBuilder desc = new StringBuilder();
        String baseDesc = description.isEmpty() ? "" : description;
        desc.append(baseDesc);
        if (!isSpecialHeader()) {
            desc.insert(0, mandatory ? "| Mandatory | " : "| Optional | ");
        }
        if (allowedValues != null) desc.append(" ").append(allowedValues);
        if (format != null) desc.append(" ").append(format);
        if (editable != null) desc.append(" ").append(editable);
        String result = desc.toString();
        return "\"" + result.replace("\"", "\"\"") + "\"";
    }

    protected boolean isSpecialHeader() {
        return header.equals(SubjectHeadersCreator.subjectTypeHeader) || header.equals(ProgramEnrolmentHeadersCreator.programHeader);
    }
}
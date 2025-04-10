package org.avni.server.importer.batch.csv.writer.header;

import org.junit.Test;

import static org.junit.jupiter.api.Assertions.*;

public class HeaderFieldTest  {

    @Test
    public void testBasicConstructorAndGetters() {
        HeaderField field = new HeaderField("Name", "User's name", true, null, null, null, false);
        assertEquals("Name", field.getHeader());
        assertEquals("\"User's name\"", field.getDescription());
    }

    @Test
    public void testMandatoryWithShouldShowIfMandatoryTrue() {
        HeaderField field = new HeaderField("Name", "User's name", true, null, null, null, true);
        assertEquals("Name", field.getHeader());
        assertEquals("\"Mandatory. User's name\"", field.getDescription());
    }

    @Test
    public void testOptionalFieldWithAllParameters() {
        HeaderField field = new HeaderField(
                "Date",
                "User's DOB",
                false,
                "Allowed values: Any date",
                "Format: DD-MM-YYYY",
                "Editable: Yes",
                true
        );
        assertEquals("Date", field.getHeader());
        assertEquals("\"Optional. User's DOB. Allowed values: Any date. Format: DD-MM-YYYY. Editable: Yes.\"", field.getDescription());
    }

    @Test
    public void testMandatoryFieldWithAllParameterss() {
        HeaderField field = new HeaderField(
                "Age",
                "User's age",
                true,
                "Allowed values: Any number",
                "Format: Integer",
                "Editable: Yes",
                true
        );
        assertEquals("Age", field.getHeader());
        assertEquals("\"Mandatory. User's age. Allowed values: Any number. Format: Integer. Editable: Yes.\"", field.getDescription());
    }

    @Test
    public void testMandatoryFieldWithDefaultShouldShowIfMandatory() {
        HeaderField field = new HeaderField("ID", "Unique ID", true, null, null, null);
        assertEquals("ID", field.getHeader());
        assertEquals("\"Mandatory.Unique ID.\"", field.getDescription());
    }

    @Test
    public void testDescriptionWithEmptyValuesAndShouldShowIfMandatoryFalse() {
        HeaderField field = new HeaderField("Field", "", false, null, null, null, false);
        assertEquals("Field", field.getHeader());
        assertEquals("\"Optional\"", field.getDescription());
    }

    @Test
    public void testDescriptionWithEmptyValuesAndShouldShowIfMandatoryTrue() {
        HeaderField field = new HeaderField("Field", "", false, null, null, null, true);
        assertEquals("Field", field.getHeader());
        assertEquals("\"Optional\"", field.getDescription());
    }

    @Test
    public void testDescriptionWithQuotes() {
        HeaderField field = new HeaderField("QuoteTest", "Contains \"quotes\"", false, null, null, null, false);
        assertEquals("QuoteTest", field.getHeader());
        assertEquals("\"Optional. Contains \"\"quotes\"\"\"", field.getDescription());
    }
}
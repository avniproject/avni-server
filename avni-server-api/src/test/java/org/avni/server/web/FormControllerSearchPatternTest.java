package org.avni.server.web;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FormControllerSearchPatternTest {
    @Test
    public void wildcardsTypedInTheSearchAreMatchedLiterally() {
        assertEquals("%\\%%", FormController.containsPattern("%"));
        assertEquals("%\\_%", FormController.containsPattern("_"));
        assertEquals("%a\\\\b%", FormController.containsPattern("a\\b"));
    }

    @Test
    public void searchIgnoresSurroundingSpacesAndLeavesCaseToTheDatabase() {
        assertEquals("%İlk Reg%", FormController.containsPattern("  İlk Reg "));
    }
}

package org.avni.server.service.abdm.hip;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

// DEMO-ONLY abdm-hackathon: never merged.
// NHA's patient-led linking: on a mobile number match, a fuzzy match on the name (USER_INIT_LINK_603).
public class AbdmNamesTest {
    @Test
    public void anAbhaNameWithAnExtraInitialMatchesTheNameAvniHolds() {
        assertTrue(AbdmNames.sameName("Lakshmi Devi", "Lakshmi Devi K"));
    }

    @Test
    public void caseAndSpacingDoNotMatter() {
        assertTrue(AbdmNames.sameName("lakshmi  DEVI", "Lakshmi Devi"));
    }

    @Test
    public void wordOrderDoesNotMatter() {
        assertTrue(AbdmNames.sameName("Devi Lakshmi", "Lakshmi Devi"));
    }

    @Test
    public void anInitialMatchesTheWordItStarts() {
        assertTrue(AbdmNames.sameName("Lakshmi D", "Lakshmi Devi"));
    }

    @Test
    public void aDifferentWordIsNotTheSameName() {
        assertFalse(AbdmNames.sameName("Lakshmi Devi", "Lakshmi Bai"));
    }

    @Test
    public void anInitialAloneIsNotAMatch() {
        assertFalse(AbdmNames.sameName("A", "Asha Devi"));
    }

    // An initial must not take the word a whole-word match needs: initial-first names are common in Tamil Nadu.
    @Test
    public void aWordAnInitialCouldTakeStillMatchesItsWholeWord() {
        assertTrue(AbdmNames.sameName("K Kavitha", "Kavitha Kannan"));
    }

    // On a shared family phone, "Lakshmi" alone must not match a relative's "Lakshmi Priya" of the same gender and year.
    @Test
    public void aOneWordNameMatchesOnlyAOneWordName() {
        assertFalse(AbdmNames.sameName("Lakshmi", "Lakshmi Priya"));
        assertTrue(AbdmNames.sameName("Lakshmi", "LAKSHMI"));
    }

    @Test
    public void namesInAnotherScriptAreCompared() {
        assertTrue(AbdmNames.sameName("லட்சுமி தேவி", "லட்சுமி தேவி"));
        assertFalse(AbdmNames.sameName("லட்சுமி தேவி", "லட்சுமி பாய்"));
    }

    @Test
    public void aBlankOrMissingNameNeverMatches() {
        assertFalse(AbdmNames.sameName("", "Lakshmi"));
        assertFalse(AbdmNames.sameName(null, "Lakshmi"));
        assertFalse(AbdmNames.sameName("Lakshmi", "  "));
    }
}

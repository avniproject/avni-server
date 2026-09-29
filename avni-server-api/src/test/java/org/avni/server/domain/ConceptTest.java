package org.avni.server.domain;

import org.avni.server.application.KeyType;
import org.avni.server.application.KeyValue;
import org.avni.server.application.KeyValues;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

public class ConceptTest {
    @Test
    public void getAnswerUUID() {
        HashMap<String, UUID> answers = new HashMap<>();
        answers.put("Male", UUID.randomUUID());
        UUID femaleUUID = UUID.randomUUID();
        answers.put("Female", femaleUUID);
        answers.put("Other", UUID.randomUUID());
        Concept concept = TestEntityFactory.createCodedConcept("Gender", answers);
        Concept femaleConcept = concept.findAnswerConcept("Female");
        assertEquals(femaleUUID.toString(), femaleConcept.getUuid());
    }

    @Test
    public void getViewColumnNameForConcept() {
        Concept concept = createConcept("Short name");
        assertEquals(concept.getName(), concept.getViewColumnName());

        checkColumnName("Short name");
        checkColumnName("This name is longer than sixty three characters the maximum allowed limit by postgres");
        checkColumnName("Pinch the skin of the abdomen. Does it go back very easily and readily");
        checkColumnName("offer the child fluid, is the child unable to drink, etc etc etc etc etc");
    }

    private void checkColumnName(String s) {
        Concept concept = createConcept(s);
        assertTrue(concept.getViewColumnName(), concept.getViewColumnName().length() <= 63);
    }

    private Concept createConcept(String name) {
        Concept concept = new Concept();
        concept.setName(name);
        return concept;
    }

    @Test
    public void hiddenKeyIsNamedAsTheFormDesignerWritesItAndThePhoneReadsIt() {
        assertEquals("hidden", KeyType.hidden.toString());
    }

    @Test
    public void isHiddenWhenTheHiddenValueIsTrue() {
        assertTrue(conceptWithHiddenValue(true).isHidden());
    }

    @Test
    public void isHiddenWhenTheHiddenValueIsTheTextTrueWithWhitespaceThePhoneAllows() {
        assertTrue(conceptWithHiddenValue("true").isHidden());
        assertTrue(conceptWithHiddenValue(" \t\ntrue\r ").isHidden());
    }

    @Test
    public void isNotHiddenForAValueThePhoneDoesNotReadAsTrue() {
        assertFalse(conceptWithHiddenValue(false).isHidden());
        assertFalse(conceptWithHiddenValue("false").isHidden());
        assertFalse(conceptWithHiddenValue("TRUE").isHidden());
        assertFalse(conceptWithHiddenValue("yes").isHidden());
        assertFalse(conceptWithHiddenValue(1).isHidden());
        assertFalse(conceptWithHiddenValue("\u000Btrue").isHidden());
        assertFalse(conceptWithHiddenValue(null).isHidden());
    }

    @Test
    public void isNotHiddenWithoutAHiddenKey() {
        assertFalse(createConcept("No key-values").isHidden());

        Concept concept = createConcept("Other key-values");
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.contact_number, "yes"));
        concept.setKeyValues(keyValues);
        assertFalse(concept.isHidden());
    }

    @Test
    public void theFirstHiddenEntryDecidesAsOnThePhone() {
        Concept concept = createConcept("Two hidden entries");
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.hidden, "no"));
        keyValues.add(new KeyValue(KeyType.hidden, true));
        concept.setKeyValues(keyValues);
        assertFalse(concept.isHidden());
    }

    private Concept conceptWithHiddenValue(Object value) {
        Concept concept = createConcept("AI verdict");
        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.hidden, value));
        concept.setKeyValues(keyValues);
        return concept;
    }
}

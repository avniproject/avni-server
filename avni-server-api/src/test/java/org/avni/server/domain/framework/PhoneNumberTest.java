package org.avni.server.domain.framework;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.Test;

import static org.junit.Assert.*;

public class PhoneNumberTest {
    @Test
    public void checkOutFeatures() throws NumberParseException {
        PhoneNumberUtil instance = PhoneNumberUtil.getInstance();
        assertTrue(isValidNumber(instance, "919455509147"));
        assertTrue(isValidNumber(instance, "91 9455509147"));
        assertTrue(isValidNumber(instance, "+91 9455509147"));
        assertTrue(isValidNumber(instance, "+919455509147"));
        assertTrue(isValidNumber(instance, "+ 91 9455509147"));
        assertTrue(isValidNumber(instance, "9455509147"));
        assertTrue(isValidNumber(instance, "09455509147"));
        assertTrue(isValidNumber(instance, " 09455509147 "));
        assertTrue(isValidNumber(instance, " 09455509147"));
        assertTrue(isValidNumber(instance, " + 91 9455509147"));
        assertTrue(isValidNumber(instance, " + 91 080 24242424"));
        assertTrue(isValidNumber(instance, " + 91 080 24242424 "));

        assertFalse(isValidNumber(instance, " +9111111111"));
        assertTrue(isValidNumber(instance, " 9111111111"));

        assertFalse(isValidNumber(instance, "94555091470"));
        assertFalse(isValidNumber(instance, "945550914"));
        assertFalse(isValidNumber(instance, "+99 9455509147"));

        assertEquals("+919455509147", instance.format(instance.parse("9455509147", "IN"), PhoneNumberUtil.PhoneNumberFormat.E164));
    }

    private static boolean isValidNumber(PhoneNumberUtil instance, String number) {
        try {
            return instance.isValidNumber(instance.parse(number, "IN"));
        } catch (NumberParseException e) {
            return false;
        }
    }
}

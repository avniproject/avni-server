package org.avni.server.domain.framework;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneNumberTest {
    @Test
    public void checkOutFeatures() throws NumberParseException {
        PhoneNumberUtil instance = PhoneNumberUtil.getInstance();
        assertTrue(isValidNumber(instance, "919245262929"));
        assertTrue(isValidNumber(instance, "91 9245262929"));
        assertTrue(isValidNumber(instance, "+91 9245262929"));
        assertTrue(isValidNumber(instance, "+919245262929"));
        assertTrue(isValidNumber(instance, "+ 91 9245262929"));
        assertTrue(isValidNumber(instance, "9245262929"));
        assertTrue(isValidNumber(instance, "09245262929"));
        assertTrue(isValidNumber(instance, " 09245262929 "));
        assertTrue(isValidNumber(instance, " 09245262929"));
        assertTrue(isValidNumber(instance, " + 91 9245262929"));
        assertTrue(isValidNumber(instance, " + 91 080 24242424"));
        assertTrue(isValidNumber(instance, " + 91 080 24242424 "));

        assertFalse(isValidNumber(instance, "92452629290"));
        assertFalse(isValidNumber(instance, "452629290"));
        assertFalse(isValidNumber(instance, "+95 9245262929"));
    }

    private static boolean isValidNumber(PhoneNumberUtil instance, String number) throws NumberParseException {
        return instance.isValidNumber(instance.parse(number, "IN"));
    }
}

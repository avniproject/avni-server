package org.avni.server.util;

import org.avni.server.domain.Account;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class PhoneNumberUtilTest {
    @Before
    public void setup() {
        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);
    }

    @Test
    public void checkFormats() {
        assertEquals("919455509147", PhoneNumberUtil.getPhoneNumberInGlificFormat("9455509147", "IN"));
        assertEquals("9455509147", PhoneNumberUtil.getNationalPhoneNumber("9455509147", "IN"));
        assertEquals("+919455509147", PhoneNumberUtil.getStandardFormatPhoneNumber("9455509147", "IN"));
    }

    @Test
    public void isValid() {
        assertFalse(PhoneNumberUtil.isValidPhoneNumber("+9111111111", "IN"));
    }

    @Test
    public void getInvalidMessage() {
        assertEquals("Invalid phone number. CountryCode:91, NationalNumber:11111111", PhoneNumberUtil.getInvalidMessage("+9111111111", "IN"));
        assertEquals("Invalid phone number. CountryCode:91, NationalNumber:2829", PhoneNumberUtil.getInvalidMessage("+91 2829", "IN"));
        assertEquals("The string supplied did not seem to be a phone number.", PhoneNumberUtil.getInvalidMessage("", "IN"));
    }
}

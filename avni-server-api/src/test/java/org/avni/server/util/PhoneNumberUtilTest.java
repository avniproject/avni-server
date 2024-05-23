package org.avni.server.util;

import org.avni.server.domain.Account;
import org.avni.server.domain.Organisation;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PhoneNumberUtilTest {
    @Test
    public void checkFormats() {
        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        assertEquals("919455509147", PhoneNumberUtil.getPhoneNumberInGlificFormat("9455509147"));
        assertEquals("9455509147", PhoneNumberUtil.getNationalPhoneNumber("9455509147"));
        assertEquals("+919455509147", PhoneNumberUtil.getStandardFormatPhoneNumber("9455509147"));
    }
}

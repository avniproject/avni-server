package org.avni.server.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import org.avni.server.framework.security.UserContextHolder;

public class PhoneNumberUtil {
    private static Phonenumber.PhoneNumber parsePhoneNumber(String phoneNumber, String region) throws NumberParseException {
        com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        return instance.parse(phoneNumber, region);
    }

    private static Phonenumber.PhoneNumber getPhoneNumber(String phoneNumber, String region) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            return instance.parse(phoneNumber, region);
        } catch (NumberParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getPhoneNumber(String phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat phoneNumberFormat, String region) {
        com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        return instance.format(getPhoneNumber(phoneNumber, region), phoneNumberFormat);
    }

    public static boolean isValidPhoneNumber(String phoneNumber, String region) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            return instance.isValidNumber(parsePhoneNumber(phoneNumber, region));
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static String getInvalidMessage(String phoneNumber, String region) {
        if (isValidPhoneNumber(phoneNumber, region)) throw new RuntimeException("Phone number is valid");

        try {
            Phonenumber.PhoneNumber pn = parsePhoneNumber(phoneNumber, region);
            return "Invalid phone number. CountryCode:" + pn.getCountryCode() + ", NationalNumber:" + pn.getNationalNumber();
        } catch (NumberParseException e) {
            return e.getMessage();
        }
    }

    public static String getStandardFormatPhoneNumber(String phoneNumber, String region) {
        return getPhoneNumber(phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164, region);
    }

    public static String getPhoneNumberInGlificFormat(String phoneNumber, String region) {
        return PhoneNumberUtil.getStandardFormatPhoneNumber(phoneNumber, region).replace("+", "");
    }

    public static String getNationalPhoneNumber(String phoneNumber, String region) {
        Phonenumber.PhoneNumber pn = getPhoneNumber(phoneNumber, region);
        return "" + pn.getNationalNumber();
    }
}

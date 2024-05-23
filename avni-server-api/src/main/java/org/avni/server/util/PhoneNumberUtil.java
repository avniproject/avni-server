package org.avni.server.util;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.Phonenumber;
import org.avni.server.framework.security.UserContextHolder;

public class PhoneNumberUtil {
    private static Phonenumber.PhoneNumber parsePhoneNumber(String phoneNumber) throws NumberParseException {
        String region = UserContextHolder.getOrganisation().getAccount().getRegion();
        com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        return instance.parse(phoneNumber, region);
    }

    public static boolean isValidPhoneNumber(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            return instance.isValidNumber(parsePhoneNumber(phoneNumber));
        } catch (NumberParseException e) {
            return false;
        }
    }

    public static String getInvalidMessage(String phoneNumber) {
        if (isValidPhoneNumber(phoneNumber)) throw new RuntimeException("Phone number is valid");

        try {
            Phonenumber.PhoneNumber pn = parsePhoneNumber(phoneNumber);
            return "Invalid phone number. CountryCode:" + pn.getCountryCode() + ", NationalNumber:" + pn.getNationalNumber();
        } catch (NumberParseException e) {
            return e.getMessage();
        }
    }

    public static String getStandardFormatPhoneNumber(String phoneNumber) {
        return getPhoneNumber(phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164);
    }

    private static Phonenumber.PhoneNumber getPhoneNumber(String phoneNumber) {
        try {
            String region = UserContextHolder.getOrganisation().getAccount().getRegion();
            com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            return instance.parse(phoneNumber, region);
        } catch (NumberParseException e) {
            throw new RuntimeException(e);
        }
    }

    private static String getPhoneNumber(String phoneNumber, com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat phoneNumberFormat) {
        com.google.i18n.phonenumbers.PhoneNumberUtil instance = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
        return instance.format(getPhoneNumber(phoneNumber), phoneNumberFormat);
    }

    public static String getPhoneNumberInGlificFormat(String phoneNumber) {
        return PhoneNumberUtil.getStandardFormatPhoneNumber(phoneNumber).replace("+", "");
    }

    public static String getNationalPhoneNumber(String phoneNumber) {
        Phonenumber.PhoneNumber pn = getPhoneNumber(phoneNumber);
        return "" + pn.getNationalNumber();
    }
}

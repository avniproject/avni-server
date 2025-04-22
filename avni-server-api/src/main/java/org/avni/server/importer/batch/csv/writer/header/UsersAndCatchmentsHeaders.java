package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.FormMapping;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class UsersAndCatchmentsHeaders implements HeaderCreator {
    public static final String LOCATION_WITH_FULL_HIERARCHY = "Location with full hierarchy";
    public static final String CATCHMENT_NAME = "Catchment Name";
    public static final String FULL_NAME_OF_USER = "Full Name of User";
    public static final String USERNAME = "Username";
    public static final String EMAIL_ADDRESS = "Email Address";
    public static final String MOBILE_NUMBER = "Mobile Number";
    public static final String PREFERRED_LANGUAGE = "Preferred Language";
    public static final String TRACK_LOCATION = "Track Location";
    public static final String DATE_PICKER_MODE = "Date picker mode";
    public static final String ENABLE_BENEFICIARY_MODE = "Enable Beneficiary mode";
    public static final String IDENTIFIER_PREFIX = "Identifier Prefix";
    public static final String USER_GROUPS = "User Groups";
    public static final String ACTIVE = "Active";

    private static final List<String> OPTIONAL_HEADERS = Arrays.asList(ENABLE_BENEFICIARY_MODE, TRACK_LOCATION, PREFERRED_LANGUAGE, DATE_PICKER_MODE, IDENTIFIER_PREFIX, USER_GROUPS, ACTIVE);

    @Override
    public String[] getAllHeaders() {
        List<String> headers = new ArrayList<>(Arrays.asList(LOCATION_WITH_FULL_HIERARCHY, CATCHMENT_NAME, USERNAME, FULL_NAME_OF_USER, EMAIL_ADDRESS, MOBILE_NUMBER, PREFERRED_LANGUAGE, TRACK_LOCATION, DATE_PICKER_MODE, ENABLE_BENEFICIARY_MODE, IDENTIFIER_PREFIX, USER_GROUPS, ACTIVE));
        return headers.toArray(new String[0]);
    }

    public String[] getMandatoryHeaders() {
        return Arrays.stream(this.getAllHeaders()).filter(header -> !OPTIONAL_HEADERS.contains(header)).toArray(String[]::new);
    }

    @Override
    public String[] getAllHeaders(FormMapping formMapping, Mode mode) {
        return getAllHeaders();
    }

    @Override
    public String[] getAllMandatoryHeaders(FormMapping formMapping, Mode mode) {
        return getMandatoryHeaders();
    }

    @Override
    public String[] getConceptHeaders(FormMapping formMapping, String[] fileHeaders) {
        return new String[0];
    }

    @Override
    public String[] getAllDescriptions(FormMapping formMapping, Mode mode) {
        return new String[0];
    }
}

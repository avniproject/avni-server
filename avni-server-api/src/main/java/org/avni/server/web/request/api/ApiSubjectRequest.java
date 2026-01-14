package org.avni.server.web.request.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.avni.server.domain.SubjectLocation;
import org.avni.server.geo.Point;
import org.avni.server.web.api.CommonFieldNames;
import org.joda.time.LocalDate;

import java.util.LinkedHashMap;
import java.util.Map;

public class ApiSubjectRequest {
    public static final String SUBJECT_TYPE = "Subject type";
    public static final String ADDRESS = "Address";
    public static final String ADDRESS_MAP = "Address map";
    public static final String DATE_OF_BIRTH = "Date of birth";
    public static final String GENDER = "Gender";
    public static final String REGISTRATION_DATE = "Registration date";
    public static final String FIRST_NAME = "First name";
    public static final String MIDDLE_NAME = "Middle name";
    public static final String LAST_NAME = "Last name";
    public static final String PROFILE_PICTURE = "Profile picture";
    public static final String REGISTRATION_LOCATION = "Registration location";
    public static final String SUBJECT_LOCATION = "Subject location";
    public static final String OBSERVATIONS = "observations";

    @JsonProperty(CommonFieldNames.EXTERNAL_ID)
    private String externalId;

    @JsonProperty(SUBJECT_TYPE)
    private String subjectType;

    @JsonProperty(ADDRESS)
    private String address;

    @JsonProperty(ADDRESS_MAP)
    private Map<String, String> addressMap;

    @JsonProperty(DATE_OF_BIRTH)
    private LocalDate dateOfBirth;

    @JsonProperty(GENDER)
    private String gender;

    @JsonProperty(REGISTRATION_DATE)
    private LocalDate registrationDate;

    @JsonProperty(FIRST_NAME)
    private String firstName;

    @JsonProperty(MIDDLE_NAME)
    private String middleName;

    @JsonProperty(LAST_NAME)
    private String lastName;

    @JsonProperty(PROFILE_PICTURE)
    private String profilePicture;

    @JsonProperty(REGISTRATION_LOCATION)
    private Point registrationLocation;

    @JsonProperty(SUBJECT_LOCATION)
    private SubjectLocation subjectLocation;

    @JsonProperty(OBSERVATIONS)
    private LinkedHashMap<String, Object> observations;

    @JsonProperty(CommonFieldNames.VOIDED)
    private boolean voided;

    public String getSubjectType() {
        return subjectType;
    }

    public void setSubjectType(String subjectType) {
        this.subjectType = subjectType;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Map<String, String> getAddressMap() {
        return addressMap;
    }

    public void setAddressMap(Map<String, String> addressMap) {
        this.addressMap = addressMap;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getRegistrationDate() {
        return registrationDate;
    }

    public void setRegistrationDate(LocalDate registrationDate) {
        this.registrationDate = registrationDate;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getMiddleName() {
        return middleName;
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getProfilePicture() {
        return profilePicture;
    }

    public void setProfilePicture(String profilePicture) {
        this.profilePicture = profilePicture;
    }

    public Point getRegistrationLocation() {
        return registrationLocation;
    }

    public void setRegistrationLocation(Point registrationLocation) {
        this.registrationLocation = registrationLocation;
    }

    public SubjectLocation getSubjectLocation() {
        return subjectLocation;
    }

    public void setSubjectLocation(SubjectLocation subjectLocation) {
        this.subjectLocation = subjectLocation;
    }

    public LinkedHashMap<String, Object> getObservations() {
        return observations;
    }

    public void setObservations(LinkedHashMap<String, Object> observations) {
        this.observations = observations;
    }

    public boolean isVoided() {
        return voided;
    }

    public void setVoided(boolean voided) {
        this.voided = voided;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }
}

package org.avni.server.domain;

public class PhoneNumber {
    String phoneNumber;
    Boolean verified;
    Boolean skipVerification;

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Boolean getVerified() {
        return verified;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public Boolean getSkipVerification() {
        return skipVerification;
    }

    public void setSkipVerification(Boolean skipVerification) {
        this.skipVerification = skipVerification;
    }
}

package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GlificContactGroupContactsResponse {
    private List<GlificContactGroupContacts> contacts;

    public List<GlificContactGroupContacts> getContacts() {
        return contacts;
    }

    public void setContacts(List<GlificContactGroupContacts> contacts) {
        this.contacts = contacts;
    }

    public static class GlificContactGroupContacts {
        private String id;
        private String maskedPhone;
        private String name;
        private String phone;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getMaskedPhone() {
            return maskedPhone;
        }

        public void setMaskedPhone(String maskedPhone) {
            this.maskedPhone = maskedPhone;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }
    }
}

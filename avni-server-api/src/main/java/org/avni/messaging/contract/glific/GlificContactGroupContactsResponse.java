package org.avni.messaging.contract.glific;

import java.util.List;

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
    }
}

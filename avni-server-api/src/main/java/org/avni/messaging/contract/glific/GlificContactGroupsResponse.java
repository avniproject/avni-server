package org.avni.messaging.contract.glific;

import java.util.List;

public class GlificContactGroupsResponse {
    private Data data;

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        this.data = data;
    }

    class ContactGroup {
        private int contactsCount;
        private String id;
        private boolean isRestricted;
        private String label;
        private int usersCount;

        public int getContactsCount() {
            return contactsCount;
        }

        public void setContactsCount(int contactsCount) {
            this.contactsCount = contactsCount;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isRestricted() {
            return isRestricted;
        }

        public void setRestricted(boolean restricted) {
            isRestricted = restricted;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public int getUsersCount() {
            return usersCount;
        }

        public void setUsersCount(int usersCount) {
            this.usersCount = usersCount;
        }
    }

    class Data {
        private List<ContactGroup> groups;

        public List<ContactGroup> getGroups() {
            return groups;
        }

        public void setGroups(List<ContactGroup> groups) {
            this.groups = groups;
        }
    }
}

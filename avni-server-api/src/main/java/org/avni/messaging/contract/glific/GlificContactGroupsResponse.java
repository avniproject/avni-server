package org.avni.messaging.contract.glific;

import java.io.Serializable;
import java.util.List;

public class GlificContactGroupsResponse {
    private List<ContactGroup> groups;

    public List<ContactGroup> getGroups() {
        return groups;
    }

    public void setGroups(List<ContactGroup> groups) {
        this.groups = groups;
    }

    public static class ContactGroup implements Serializable  {
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

        @Override
        public String toString() {
            return "ContactGroup{" +
                    "contactsCount=" + contactsCount +
                    ", id='" + id + '\'' +
                    ", label='" + label + '\'' +
                    ", usersCount=" + usersCount +
                    '}';
        }
    }
}

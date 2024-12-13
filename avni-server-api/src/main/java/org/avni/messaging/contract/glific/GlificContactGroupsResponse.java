package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.io.Serializable;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
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
        private String label;
        private int usersCount;
        private String description;

        public int getContactsCount() {
            return contactsCount;
        }

        public void setContactsCount(int contactsCount) {
            this.contactsCount = contactsCount;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
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

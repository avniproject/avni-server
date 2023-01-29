package org.avni.messaging.domain;

import java.io.Serializable;

public class NextTriggerDetails implements Serializable {
    private int pageNo;
    private String contactId;

    public NextTriggerDetails() {}

    public NextTriggerDetails(int pageNo, String contactId) {
        this.pageNo = pageNo;
        this.contactId = contactId;
    }

    public int getPageNo() {
        return pageNo;
    }

    public void setPageNo(int pageNo) {
        this.pageNo = pageNo;
    }

    public String getContactId() {
        return contactId;
    }

    public void setContactId(String contactId) {
        this.contactId = contactId;
    }
}

package org.avni.messaging.contract;

import org.avni.messaging.contract.glific.GlificGetGroupResponse;
import org.avni.server.web.contract.WebPagedResponse;

public class GroupContactsResponse {
    private WebPagedResponse contacts;
    private GlificGetGroupResponse.GlificGroup group;

    public GroupContactsResponse() {
    }

    public GroupContactsResponse(WebPagedResponse webPagedResponse, GlificGetGroupResponse.GlificGroup group) {
        this.contacts = webPagedResponse;
        this.group = group;
    }

    public WebPagedResponse getContacts() {
        return contacts;
    }

    public void setContacts(WebPagedResponse contacts) {
        this.contacts = contacts;
    }

    public GlificGetGroupResponse.GlificGroup getGroup() {
        return group;
    }

    public void setGroup(GlificGetGroupResponse.GlificGroup group) {
        this.group = group;
    }
}

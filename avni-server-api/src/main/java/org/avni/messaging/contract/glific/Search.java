package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "contact",
    "messages"
})
@JsonIgnoreProperties(ignoreUnknown = true)
public class Search {

    @JsonProperty("contact")
    private GlificContactResponse contact;
    @JsonProperty("messages")
    private List<Message> messages = new ArrayList<Message>();

    /**
     * No args constructor for use in serialization
     *
     */
    public Search() {
    }

    /**
     *
     * @param contact
     * @param messages
     */
    public Search(GlificContactResponse contact, List<Message> messages) {
        super();
        this.contact = contact;
        this.messages = messages;
    }

    @JsonProperty("contact")
    public GlificContactResponse getContact() {
        return contact;
    }

    @JsonProperty("contact")
    public void setContact(GlificContactResponse contact) {
        this.contact = contact;
    }

    @JsonProperty("messages")
    public List<Message> getMessages() {
        return messages;
    }

    @JsonProperty("messages")
    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

}

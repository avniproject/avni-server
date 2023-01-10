package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "messages"
})
public class GetAllMessagesData {

    @JsonProperty("messages")
    private List<Message> messages = new ArrayList<Message>();

    /**
     * No args constructor for use in serialization
     * 
     */
    public GetAllMessagesData() {
    }

    /**
     * 
     * @param messages
     */
    public GetAllMessagesData(List<Message> messages) {
        super();
        this.messages = messages;
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
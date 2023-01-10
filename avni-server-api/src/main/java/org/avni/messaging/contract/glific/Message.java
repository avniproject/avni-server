package org.avni.messaging.contract.glific;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({
    "body",
    "id",
    "insertedAt",
    "sendAt",
    "sender",
    "receiver",
    "type"
})
public class Message {

    @JsonProperty("body")
    private String body;
    @JsonProperty("id")
    private String id;
    @JsonProperty("insertedAt")
    private String insertedAt;
    @JsonProperty("sendAt")
    private String sendAt;
    @JsonProperty("sender")
    private GlificContactResponse sender;
    @JsonProperty("receiver")
    private GlificContactResponse receiver;
    @JsonProperty("type")
    private String type;

    /**
     * No args constructor for use in serialization
     * 
     */
    public Message() {
    }

    /**
     * 
     * @param receiver
     * @param insertedAt
     * @param sender
     * @param id
     * @param sendAt
     * @param body
     * @param type
     */
    public Message(String body, String id, String insertedAt, String sendAt, GlificContactResponse sender, GlificContactResponse receiver, String type) {
        super();
        this.body = body;
        this.id = id;
        this.insertedAt = insertedAt;
        this.sendAt = sendAt;
        this.sender = sender;
        this.receiver = receiver;
        this.type = type;
    }

    @JsonProperty("body")
    public String getBody() {
        return body;
    }

    @JsonProperty("body")
    public void setBody(String body) {
        this.body = body;
    }

    @JsonProperty("id")
    public String getId() {
        return id;
    }

    @JsonProperty("id")
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty("insertedAt")
    public String getInsertedAt() {
        return insertedAt;
    }

    @JsonProperty("insertedAt")
    public void setInsertedAt(String insertedAt) {
        this.insertedAt = insertedAt;
    }

    @JsonProperty("sendAt")
    public String getSendAt() {
        return sendAt;
    }

    @JsonProperty("sendAt")
    public void setSendAt(String sendAt) {
        this.sendAt = sendAt;
    }

    @JsonProperty("sender")
    public GlificContactResponse getSender() {
        return sender;
    }

    @JsonProperty("sender")
    public void setSender(GlificContactResponse sender) {
        this.sender = sender;
    }

    @JsonProperty("receiver")
    public GlificContactResponse getReceiver() {
        return receiver;
    }

    @JsonProperty("receiver")
    public void setReceiver(GlificContactResponse receiver) {
        this.receiver = receiver;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }

}

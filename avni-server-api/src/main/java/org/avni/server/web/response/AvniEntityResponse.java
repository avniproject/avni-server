package org.avni.server.web.response;

import org.avni.server.domain.CHSBaseEntity;

public class AvniEntityResponse {
    private long id;
    private String uuid;

    private boolean success;
    private String errorMessage;

    private AvniEntityResponse() {
    }

    public AvniEntityResponse(CHSBaseEntity entity) {
        this.id = entity.getId();
        this.uuid = entity.getUuid();
        this.success = true;
    }

    public static AvniEntityResponse error(String errorMessage) {
        AvniEntityResponse response = new AvniEntityResponse();
        response.success = false;
        response.errorMessage = errorMessage;
        return response;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}

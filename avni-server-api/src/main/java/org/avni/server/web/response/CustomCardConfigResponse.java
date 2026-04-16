package org.avni.server.web.response;

import org.avni.server.domain.CustomCardConfig;

public class CustomCardConfigResponse {
    private String uuid;
    private String name;
    private String htmlFileS3Key;
    private String dataRule;
    private boolean voided;

    public static CustomCardConfigResponse from(CustomCardConfig entity) {
        CustomCardConfigResponse response = new CustomCardConfigResponse();
        response.uuid = entity.getUuid();
        response.name = entity.getName();
        response.htmlFileS3Key = entity.getHtmlFileS3Key();
        response.dataRule = entity.getDataRule();
        response.voided = entity.isVoided();
        return response;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getHtmlFileS3Key() {
        return htmlFileS3Key;
    }

    public String getDataRule() {
        return dataRule;
    }

    public boolean isVoided() {
        return voided;
    }
}

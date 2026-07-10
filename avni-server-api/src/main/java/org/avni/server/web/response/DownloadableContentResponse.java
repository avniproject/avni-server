package org.avni.server.web.response;

import org.avni.server.domain.DownloadableContent;

import java.util.Map;

public class DownloadableContentResponse {
    private String uuid;
    private String name;
    private String category;
    private String contentKey;
    private String sha256;
    private boolean needsKey;
    private boolean voided;
    private Map<String, Object> payload;

    public static DownloadableContentResponse from(DownloadableContent entity) {
        DownloadableContentResponse response = new DownloadableContentResponse();
        response.uuid = entity.getUuid();
        response.name = entity.getName();
        response.category = entity.getCategory();
        response.contentKey = entity.getContentKey();
        response.sha256 = entity.getSha256();
        response.needsKey = entity.isNeedsKey();
        response.voided = entity.isVoided();
        response.payload = entity.getPayload();
        return response;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    public String getContentKey() {
        return contentKey;
    }

    public String getSha256() {
        return sha256;
    }

    public boolean isNeedsKey() {
        return needsKey;
    }

    public boolean isVoided() {
        return voided;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }
}

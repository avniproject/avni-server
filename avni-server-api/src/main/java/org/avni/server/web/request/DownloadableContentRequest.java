package org.avni.server.web.request;

import java.util.Map;

public class DownloadableContentRequest extends CHSRequest {
    private String name;
    private String category;
    private String contentKey;
    private String sha256;
    private boolean needsKey;
    private Map<String, Object> payload;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getContentKey() {
        return contentKey;
    }

    public void setContentKey(String contentKey) {
        this.contentKey = contentKey;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public boolean isNeedsKey() {
        return needsKey;
    }

    public void setNeedsKey(boolean needsKey) {
        this.needsKey = needsKey;
    }

    public Map<String, Object> getPayload() {
        return payload;
    }

    public void setPayload(Map<String, Object> payload) {
        this.payload = payload;
    }
}

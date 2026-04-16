package org.avni.server.web.request;

public class CustomCardConfigRequest extends CHSRequest {
    private String name;
    private String htmlFileS3Key;
    private String dataRule;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHtmlFileS3Key() {
        return htmlFileS3Key;
    }

    public void setHtmlFileS3Key(String htmlFileS3Key) {
        this.htmlFileS3Key = htmlFileS3Key;
    }

    public String getDataRule() {
        return dataRule;
    }

    public void setDataRule(String dataRule) {
        this.dataRule = dataRule;
    }
}

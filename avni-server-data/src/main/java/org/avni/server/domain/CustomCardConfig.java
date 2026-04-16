package org.avni.server.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.hibernate.annotations.BatchSize;
import org.springframework.util.StringUtils;

@Entity
@Table(name = "custom_card_config")
@BatchSize(size = 100)
public class CustomCardConfig extends OrganisationAwareEntity {
    @NotNull
    private String name;

    @Column(name = "html_file_s3_key")
    private String htmlFileS3Key;

    @Column(name = "data_rule")
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
        this.htmlFileS3Key = StringUtils.isEmpty(htmlFileS3Key) ? null : htmlFileS3Key;
    }

    public String getDataRule() {
        return dataRule;
    }

    public void setDataRule(String dataRule) {
        this.dataRule = dataRule;
    }
}

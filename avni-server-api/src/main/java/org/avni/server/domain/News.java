package org.avni.server.domain;

import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.joda.time.DateTime;

import jakarta.persistence.Column;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@Entity
@Table(name = "news")
@BatchSize(size = 100)
public class News extends OrganisationAwareEntity {
    @NotNull
    private String title;
    private Instant publishedDate;
    private String heroImage;
    private String content;
    @Column(name = "contenthtml")
    private String contentHtml;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DateTime getPublishedDate() {
        return DateTimeUtil.toJodaDateTime(publishedDate);
    }

    public void setPublishedDate(DateTime publishedDate) {
        this.publishedDate = DateTimeUtil.toInstant(publishedDate);
    }

    public String getHeroImage() {
        return heroImage;
    }

    public void setHeroImage(String heroImage) {
        this.heroImage = heroImage;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHtml() {
        return contentHtml;
    }

    public void setContentHtml(String contentHtml) {
        this.contentHtml = contentHtml;
    }
}

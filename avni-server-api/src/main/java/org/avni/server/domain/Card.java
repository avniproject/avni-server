package org.avni.server.domain;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "report_card")
@BatchSize(size = 100)
@JsonIgnoreProperties({"standardReportCardType"})
public class Card extends OrganisationAwareEntity {

    public static final int INT_CONSTANT_DEFAULT_COUNT_OF_CARDS = 1;
    public static final int INT_CONSTANT_MAX_COUNT_OF_CARDS = 9;
    @NotNull
    private String name;

    private String query;

    private String description;

    private String colour;

    private boolean nested = false;

    private int countOfCards = INT_CONSTANT_DEFAULT_COUNT_OF_CARDS;

    @Column(name = "icon_file_s3_key")
    private String iconFileS3Key;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "standard_report_card_type_id")
    private StandardReportCardType standardReportCardType;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColour() {
        return colour;
    }

    public void setColour(String colour) {
        this.colour = colour;
    }

    public boolean isNested() {
        return getStandardReportCardType() == null && nested;
    }

    public void setNested(boolean nested) {
        this.nested = getStandardReportCardType() == null && nested;
    }

    public int getCountOfCards() {
        return isNested() ? this.countOfCards : INT_CONSTANT_DEFAULT_COUNT_OF_CARDS;
    }

    public void setCountOfCards(int countOfCards) {
        this.countOfCards = isNested() ? countOfCards : INT_CONSTANT_DEFAULT_COUNT_OF_CARDS;
    }

    public StandardReportCardType getStandardReportCardType() {
        return standardReportCardType;
    }

    public void setStandardReportCardType(StandardReportCardType standardReportCardType) {
        Boolean reportCardTypeChangedFromQueryToStandard = (this.standardReportCardType == null && standardReportCardType != null);
        this.standardReportCardType = standardReportCardType;
        resetNestedCardInfoOnlyApplicableForQueryReportCardType(reportCardTypeChangedFromQueryToStandard);
    }

    private void resetNestedCardInfoOnlyApplicableForQueryReportCardType(Boolean reportCardTypeChangedFromQueryToStandard) {
        if (reportCardTypeChangedFromQueryToStandard) {
            this.setNested(false);
            this.setCountOfCards(INT_CONSTANT_DEFAULT_COUNT_OF_CARDS);
        }
    }

    public Long getStandardReportCardTypeId() {
        return standardReportCardType == null ? null : standardReportCardType.getId();
    }

    public String getIconFileS3Key() {
        return iconFileS3Key;
    }

    public void setIconFileS3Key(String iconFileS3Key) {
        this.iconFileS3Key = iconFileS3Key;
    }
}

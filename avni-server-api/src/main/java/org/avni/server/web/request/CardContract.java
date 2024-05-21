package org.avni.server.web.request;

import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;

import java.util.ArrayList;
import java.util.List;

public class CardContract extends CHSRequest {
    private String name;
    private String query;
    private String description;
    private String color;
    private Double displayOrder;
    private Long standardReportCardTypeId;
    private StandardReportCardTypeContract standardReportCardType;
    private String iconFileS3Key;
    private boolean nested;
    private int count;
    private List<SubjectTypeContract> standardReportCardInputSubjectTypes = new ArrayList<>();
    private List<ProgramContract> standardReportCardInputPrograms = new ArrayList<>();
    private List<EncounterTypeContract> standardReportCardInputEncounterTypes = new ArrayList<>();

    public Long getStandardReportCardTypeId() {
        return standardReportCardTypeId;
    }

    public void setStandardReportCardTypeId(Long standardReportCardTypeId) {
        this.standardReportCardTypeId = standardReportCardTypeId;
    }

    public Double getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Double displayOrder) {
        this.displayOrder = displayOrder;
    }

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

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String getIconFileS3Key() {
        return iconFileS3Key;
    }

    public void setIconFileS3Key(String iconFileS3Key) {
        this.iconFileS3Key = iconFileS3Key;
    }

    public boolean isNested() {
        return nested;
    }

    public void setNested(boolean nested) {
        this.nested = nested;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public StandardReportCardTypeContract getStandardReportCardType() {
        return standardReportCardType;
    }

    public void setStandardReportCardType(StandardReportCardTypeContract standardReportCardType) {
        this.standardReportCardType = standardReportCardType;
    }

    public List<SubjectTypeContract> getStandardReportCardInputSubjectTypes() {
        return standardReportCardInputSubjectTypes;
    }

    public void setStandardReportCardInputSubjectTypes(List<SubjectTypeContract> standardReportCardInputSubjectTypes) {
        this.standardReportCardInputSubjectTypes = standardReportCardInputSubjectTypes;
    }

    public List<ProgramContract> getStandardReportCardInputPrograms() {
        return standardReportCardInputPrograms;
    }

    public void setStandardReportCardInputPrograms(List<ProgramContract> standardReportCardInputPrograms) {
        this.standardReportCardInputPrograms = standardReportCardInputPrograms;
    }

    public List<EncounterTypeContract> getStandardReportCardInputEncounterTypes() {
        return standardReportCardInputEncounterTypes;
    }

    public void setStandardReportCardInputEncounterTypes(List<EncounterTypeContract> standardReportCardInputEncounterTypes) {
        this.standardReportCardInputEncounterTypes = standardReportCardInputEncounterTypes;
    }
}

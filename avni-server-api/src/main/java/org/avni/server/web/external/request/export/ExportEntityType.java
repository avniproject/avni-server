package org.avni.server.web.external.request.export;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.web.external.request.export.ExportFilters;

import java.beans.Transient;
import java.util.*;
import java.util.stream.Collectors;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportEntityType {
    private String uuid;
    private List<String> fields = new ArrayList<>();
    private ExportFilters filters;
    private long maxCount = 1;

    private transient List<String> outputFields = new ArrayList<>();

    public ExportFilters getFilters() {
        return filters == null ? new ExportFilters() : filters;
    }

    public boolean isDateEmpty() {
        return this.filters == null || this.filters.getDate() == null ||
                this.filters.getDate().getTo() == null || this.filters.getDate().getFrom() == null;
    }

    public void setFilters(ExportFilters filters) {
        this.filters = filters;
    }

    public long getMaxCount() {
        return maxCount;
    }

    public void setMaxCount(Long maxCount) {
        this.maxCount = maxCount;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public List<String> getFields() {
        return fields;
    }

    public boolean isEmptyOrContains(String conceptUUID) {
        return fields.isEmpty() || fields.contains(conceptUUID);
    }

    public void setFields(List<String> fields) {
        this.fields = fields;
    }

    public long getNoOfFields() {
        return getFields().size();
    }

    public long getTotalNumberOfColumns() {
        return getEffectiveNoOfFields() * getMaxCount();
    }

    public long getEffectiveNoOfFields() {
        return getNoOfFields();
    }

    public List<String> getOutputFields() {
        return outputFields;
    }

    public void setOutputFields(List<String> outputFields) {
        this.outputFields = outputFields;
    }

    public boolean hasUserProvided(Concept concept) {
        return outputFields.stream().anyMatch(s -> s.equals(concept.getName()));
    }

    public void removeField(Concept concept) {
        outputFields.remove(concept.getName());
    }

    public boolean hasUserProvidedFields() {
        return outputFields.size() > 0;
    }

    public void addField(ConceptAnswer conceptAnswer) {
        outputFields.add(conceptAnswer.getAnswerConcept().getName());
    }

    public void addField(Concept concept) {
        outputFields.add(concept.getName());
    }

    public void addFields(Collection<String> outputFields) {
        this.outputFields.addAll(outputFields);
    }

    public void addAllUserProvidedFields() {
        this.outputFields.addAll(fields);
    }
}

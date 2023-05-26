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

    public boolean hasInputField(Concept concept) {
        return fields.stream().anyMatch(s -> s.equals(concept.getName()));
    }

    public List<ExportEntityType> getAllExportEntityTypes() {
        return Collections.singletonList(this);
    }
}

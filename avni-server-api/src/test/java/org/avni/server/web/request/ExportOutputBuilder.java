package org.avni.server.web.request;

import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.external.request.export.ExportFilters;
import org.avni.server.web.external.request.export.ExportOutput;

import java.util.List;

public class ExportOutputBuilder {
    private final ExportOutput exportOutput = new ExportOutput();

    public ExportOutputBuilder forSubjectType(String subjectTypeUUID) {
        exportOutput.setUuid(subjectTypeUUID);
        return this;
    }

    public ExportOutputBuilder withFields(List<String> fields) {
        exportOutput.setFields(fields);
        return this;
    }

    public ExportOutputBuilder usingFilters(ExportFilters exportFilters) {
        exportOutput.setFilters(exportFilters);
        return this;
    }

    public ExportOutputBuilder withEncounterTypes(List<ExportEntityType> encounterTypes) {
        exportOutput.setEncounters(encounterTypes);
    	return this;
    }

    public ExportOutputBuilder withProgram(List<ExportOutput.ExportNestedOutput> programs) {
        exportOutput.setPrograms(programs);
    	return this;
    }

    public ExportOutput build() {
        return exportOutput;
    }
}

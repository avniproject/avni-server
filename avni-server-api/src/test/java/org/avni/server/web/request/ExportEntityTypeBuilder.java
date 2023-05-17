package org.avni.server.web.request;

import org.avni.server.web.external.request.export.ExportEntityType;

import java.util.List;

public class ExportEntityTypeBuilder {
    private final ExportEntityType exportEntityType = new ExportEntityType();

    public ExportEntityType build() {
        return exportEntityType;
    }

    public ExportEntityTypeBuilder forSubjectType(String subjectTypeUUID) {
        exportEntityType.setUuid(subjectTypeUUID);
        return this;
    }

    public ExportEntityTypeBuilder withFields(List<String> fields) {
        exportEntityType.setFields(fields);
        return this;
    }

    public ExportEntityTypeBuilder withUuid(String uuid) {
        exportEntityType.setUuid(uuid);
    	return this;
    }
}

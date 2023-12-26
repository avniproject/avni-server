package org.avni.server.web.response;

import org.avni.server.domain.ConceptDataType;

public class ConceptNameUuidAndDatatype {
    private String uuid;
    private String name;
    private ConceptDataType conceptDataType;

    public ConceptNameUuidAndDatatype(String uuid, String name, ConceptDataType conceptDataType) {
        this.uuid = uuid;
        this.name = name;
        this.conceptDataType = conceptDataType;
    }

    public String getUuid() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public ConceptDataType getConceptDataType() {
        return conceptDataType;
    }
}

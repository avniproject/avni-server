package org.avni.server.service.builder.identifier;

import org.avni.server.domain.Catchment;
import org.avni.server.domain.IdentifierSource;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.identifier.IdentifierGeneratorType;

import java.util.UUID;

public class IdentifierSourceBuilder {
    private final IdentifierSource identifierSource = new IdentifierSource();

    public IdentifierSourceBuilder() {
        String uuid = UUID.randomUUID().toString();
        setUuid(uuid).setName(uuid).setMaxLength(10).setMinLength(3).setBatchGenerationSize(100l).setMinimumBalance(10l);
    }

    public IdentifierSourceBuilder setUuid(String uuid) {
        identifierSource.setUuid(uuid);
        return this;
    }

    public IdentifierSourceBuilder setName(String name) {
        identifierSource.setName(name);
        return this;
    }

    public IdentifierSourceBuilder setType(IdentifierGeneratorType type) {
        identifierSource.setType(type);
        return this;
    }

    public IdentifierSourceBuilder setCatchment(Catchment catchment) {
        identifierSource.setCatchment(catchment);
        return this;
    }

    public IdentifierSourceBuilder setMinimumBalance(Long minimumBalance) {
        identifierSource.setMinimumBalance(minimumBalance);
        return this;
    }

    public IdentifierSourceBuilder setBatchGenerationSize(Long batchGenerationSize) {
        identifierSource.setBatchGenerationSize(batchGenerationSize);
        return this;
    }

    public IdentifierSourceBuilder setOptions(JsonObject options) {
        identifierSource.setOptions(options);
        return this;
    }

    public IdentifierSourceBuilder setMinLength(Integer minLength) {
        identifierSource.setMinLength(minLength);
        return this;
    }

    public IdentifierSourceBuilder setMaxLength(Integer maxLength) {
        identifierSource.setMaxLength(maxLength);
        return this;
    }

    public IdentifierSourceBuilder addPrefix(String value) {
        identifierSource.addPrefix(value);
        return this;
    }

    public IdentifierSource build() {
        return identifierSource;
    }
}

package org.avni.server.domain;

import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import javax.persistence.*;
import javax.validation.constraints.NotNull;

@Entity
@Table(name = "identifier_source")
@BatchSize(size = 100)
public class IdentifierSource extends OrganisationAwareEntity {
    private static final String PREFIX = "prefix";

    @NotNull
    private String name;

    @Column
    @NotNull
    @Enumerated(EnumType.STRING)
    private IdentifierGeneratorType type;

    @ManyToOne(fetch= FetchType.LAZY)
    @JoinColumn(name = "catchment_id")
    private Catchment catchment;

    @Column
    private Long minimumBalance;

    @Column
    private Long batchGenerationSize;

    @Column
    @Type(type = "jsonObject")
    private JsonObject options;

    @Column
    private Integer minLength;

    @Column
    private Integer maxLength;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public IdentifierGeneratorType getType() {
        return type;
    }

    public void setType(IdentifierGeneratorType type) {
        this.type = type;
    }

    public Catchment getCatchment() {
        return catchment;
    }

    public void setCatchment(Catchment catchment) {
        this.catchment = catchment;
    }

    public Long getMinimumBalance() {
        return minimumBalance;
    }

    public void setMinimumBalance(Long minimumBalance) {
        this.minimumBalance = minimumBalance;
    }

    public Long getBatchGenerationSize() {
        return batchGenerationSize;
    }

    public void setBatchGenerationSize(Long batchGenerationSize) {
        this.batchGenerationSize = batchGenerationSize;
    }

    public JsonObject getOptions() {
        return options;
    }

    public void setOptions(JsonObject options) {
        this.options = options;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }

    public String getPrefix() {
        return this.getOptions().getString(PREFIX);
    }

    public void addPrefix(String value) {
        if (this.options == null)
            this.options = new JsonObject();
        this.options.with(PREFIX, value);
    }
}

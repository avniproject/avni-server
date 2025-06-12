package org.avni.server.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.common.dbSchema.ColumnNames;
import org.avni.server.common.dbSchema.TableNames;
import org.avni.server.domain.sync.SubjectLinkedSyncEntity;
import org.avni.server.domain.sync.SyncDisabledEntityHelper;
import org.avni.server.framework.hibernate.JodaDateTimeConverter;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.avni.server.geo.Point;
import org.avni.server.geo.PointType;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

import static org.avni.server.common.dbSchema.ColumnNames.ProgramEnrolmentExitObservations;
import static org.avni.server.common.dbSchema.ColumnNames.ProgramEnrolmentObservations;

@Entity
@Table(name = TableNames.ProgramEnrolment)
@JsonIgnoreProperties({"programEncounters", "individual"})
@BatchSize(size = 100)
public class ProgramEnrolment extends SyncAttributeEntity implements MessageableEntity, SubjectLinkedSyncEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "program_id")
    private Program program;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_id")
    private Individual individual;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true, mappedBy = "programEnrolment")
    private Set<ProgramEncounter> programEncounters;

    @Column(name = ColumnNames.EnrolmentDateTime)
    @NotNull
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime enrolmentDateTime;

    @Column(name = ProgramEnrolmentObservations)
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection observations;

    @Column(name = ColumnNames.ProgramExitDateTime)
    @Convert(converter = JodaDateTimeConverter.class)
    private DateTime programExitDateTime;

    @Type(value = PointType.class)
    @Column
    private Point enrolmentLocation;

    @Type(value = PointType.class)
    @Column
    private Point exitLocation;

    @Column(name = ProgramEnrolmentExitObservations)
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection programExitObservations;

    @Column
    private String legacyId;

    @Column(name = "address_id")
    private Long addressId;

    @Column
    private boolean syncDisabled;

    @NotNull
    private Date syncDisabledDateTime;

    public boolean isExited() {
        return this.getProgramExitDateTime() != null;
    }

    public Program getProgram() {
        return program;
    }

    public void setProgram(Program program) {
        this.program = program;
    }

    public Individual getIndividual() {
        return individual;
    }

    public void setIndividual(Individual individual) {
        this.individual = individual;
    }

    public Set<ProgramEncounter> getProgramEncounters() {
        if (programEncounters == null) {
            programEncounters = new HashSet<>();
        }
        return programEncounters;
    }

    @JsonIgnore
    public Stream<ProgramEncounter> getEncounters(boolean removeCancelledEncounters) {
        return this.nonVoidedEncounters().filter(enc -> removeCancelledEncounters ? enc.getCancelDateTime() == null : true);
    }

    @JsonIgnore
    public Stream<ProgramEncounter> getEncountersOfType(String encounterTypeName, boolean removeCancelledEncounters) {
        return this.getEncounters(removeCancelledEncounters).filter(enc -> enc.getEncounterType().getName().equals(encounterTypeName));
    }

    @JsonIgnore
    public Stream<ProgramEncounter> nonVoidedEncounters() {
        return this.getProgramEncounters().stream().filter(enc -> !enc.isVoided());
    }

    @JsonIgnore
    public Stream<ProgramEncounter> scheduledEncounters() {
        return this.getEncounters(true).filter(enc -> enc.getEncounterDateTime() == null && enc.getCancelDateTime() == null);
    }

    @JsonIgnore
    public Stream<ProgramEncounter> scheduledEncountersOfType(String encounterTypeName) {
        return this.scheduledEncounters().filter(scheduledEncounter -> scheduledEncounter.getEncounterType().getOperationalEncounterTypeName().equals(encounterTypeName));
    }

    public void setProgramEncounters(Set<ProgramEncounter> programEncounters) {
        this.programEncounters = programEncounters;
    }

    public DateTime getEnrolmentDateTime() {
        return enrolmentDateTime;
    }

    public void setEnrolmentDateTime(DateTime enrolmentDateTime) {
        this.enrolmentDateTime = enrolmentDateTime;
    }

    public ObservationCollection getObservations() {
        return observations;
    }

    public void setObservations(ObservationCollection observations) {
        this.observations = observations;
    }

    public DateTime getProgramExitDateTime() {
        return programExitDateTime;
    }

    public void setProgramExitDateTime(DateTime programExitDateTime) {
        this.programExitDateTime = programExitDateTime;
    }

    public ObservationCollection getProgramExitObservations() {
        return programExitObservations;
    }

    public void setProgramExitObservations(ObservationCollection programExitObservations) {
        this.programExitObservations = programExitObservations;
    }

    public ProgramEncounter findEncounter(String encounterTypeName, String encounterName) {
        return this.getProgramEncounters().stream().filter(programEncounter -> programEncounter.matches(encounterTypeName, encounterName)).findAny().orElse(null);
    }

    public Point getEnrolmentLocation() {
        return enrolmentLocation;
    }

    public void setEnrolmentLocation(Point enrolmentLocation) {
        this.enrolmentLocation = enrolmentLocation;
    }

    public Point getExitLocation() {
        return exitLocation;
    }

    public void setExitLocation(Point exitLocation) {
        this.exitLocation = exitLocation;
    }

    public void setLegacyId(String legacyId) {
        this.legacyId = legacyId;
    }

    public String getLegacyId() {
        return legacyId;
    }

    @JsonIgnore
    public Long getAddressId() {
        return addressId;
    }

    public void setAddressId(Long addressId) {
        this.addressId = addressId;
    }

    public boolean isSyncDisabled() {
        return syncDisabled;
    }

    public void setSyncDisabled(boolean syncDisabled) {
        this.syncDisabled = syncDisabled;
    }

    @Override
    public Date getSyncDisabledDateTime() {
        return this.syncDisabledDateTime;
    }

    @Override
    @JsonIgnore
    public Long getEntityTypeId() {
        return getProgram().getId();
    }

    @Override
    @JsonIgnore
    public Long getEntityId() {
        return getId();
    }

    public boolean isActive() {
        return this.getProgramExitDateTime() == null && !this.isVoided();
    }

    public void setSyncDisabledDateTime(Date syncDisabledDateTime) {
        this.syncDisabledDateTime = syncDisabledDateTime;
    }

    @PrePersist
    public void beforeSave() {
        SyncDisabledEntityHelper.handleSave(this, this.getIndividual());
    }

    @PreUpdate
    public void beforeUpdate() {
        SyncDisabledEntityHelper.handleSave(this, this.getIndividual());
    }
}

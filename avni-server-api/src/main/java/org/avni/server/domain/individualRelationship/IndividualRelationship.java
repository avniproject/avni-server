package org.avni.server.domain.individualRelationship;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import org.avni.server.domain.Individual;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.domain.OrganisationAwareEntity;
import org.avni.server.framework.hibernate.ObservationCollectionUserType;
import org.avni.server.util.DateTimeUtil;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;
import org.joda.time.DateTime;

import java.time.Instant;

@Entity
@Table(name = "individual_relationship")
@JsonIgnoreProperties({"individuala", "individualB"})
@BatchSize(size = 100)
public class IndividualRelationship extends OrganisationAwareEntity {
    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "relationship_type_id")
    private IndividualRelationshipType relationship;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_a_id")
    //Repository query parser fails if i name this individualA
    private Individual individuala;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "individual_b_id")
    private Individual individualB;

    @Column
    private Instant enterDateTime;

    @Column
    private Instant exitDateTime;

    @Column
    @Type(value = ObservationCollectionUserType.class)
    private ObservationCollection exitObservations;

    public IndividualRelationshipType getRelationship() {
        return relationship;
    }

    public void setRelationship(IndividualRelationshipType relationship) {
        this.relationship = relationship;
    }

    public Individual getIndividuala() {
        return individuala;
    }

    public void setIndividuala(Individual individualA) {
        this.individuala = individualA;
    }

    public Individual getIndividualB() {
        return individualB;
    }

    public void setIndividualB(Individual individualB) {
        this.individualB = individualB;
    }

    public DateTime getEnterDateTime() {
        return DateTimeUtil.toJodaDateTime(enterDateTime);
    }

    public void setEnterDateTime(DateTime enterDateTime) {
        this.enterDateTime = DateTimeUtil.toInstant(enterDateTime);
    }

    public DateTime getExitDateTime() {
        return DateTimeUtil.toJodaDateTime(exitDateTime);
    }

    public void setExitDateTime(DateTime exitDateTime) {
        this.exitDateTime = DateTimeUtil.toInstant(exitDateTime);
    }

    public ObservationCollection getExitObservations() {
        return exitObservations;
    }

    public void setExitObservations(ObservationCollection exitObservations) {
        this.exitObservations = exitObservations;
    }
}

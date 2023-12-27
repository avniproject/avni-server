package org.avni.server.domain;

import org.avni.server.domain.identifier.IdentifierGeneratorType;
import org.hibernate.annotations.BatchSize;

import javax.persistence.*;

@Entity
@Table(name = "identifier_user_assignment")
@BatchSize(size = 100)
public class IdentifierUserAssignment extends OrganisationAwareEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "identifier_source_id")
    private IdentifierSource identifierSource;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to_user_id")
    private User assignedTo;

    @Column
    private String identifierStart;

    @Column
    private String identifierEnd;

    @Column
    private String lastAssignedIdentifier;

    public IdentifierSource getIdentifierSource() {
        return identifierSource;
    }

    public void setIdentifierSource(IdentifierSource identifierSource) {
        this.identifierSource = identifierSource;
    }

    public User getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(User assignedTo) {
        this.assignedTo = assignedTo;
    }

    public String getIdentifierStart() {
        return identifierStart;
    }

    public void setIdentifierStart(String identifierStart) {
        this.identifierStart = identifierStart;
    }

    public String getIdentifierEnd() {
        return identifierEnd;
    }

    public void setIdentifierEnd(String identifierEnd) {
        this.identifierEnd = identifierEnd;
    }

    public String getLastAssignedIdentifier() {
        return lastAssignedIdentifier;
    }

    public void setLastAssignedIdentifier(String lastAssignedIdentifier) {
        this.lastAssignedIdentifier = lastAssignedIdentifier;
    }

    public boolean isExhausted() {
        return getLastAssignedIdentifier() != null && getLastAssignedIdentifier().equals(getIdentifierEnd());
    }

    public void validate() throws ValidationException {
        String prefix = identifierSource.getType().equals(IdentifierGeneratorType.userPoolBasedIdentifierGenerator) ? identifierSource.getPrefix() : assignedTo.getUserSettings().getIdPrefix();
        if (Long.parseLong(identifierStart.replace(prefix, "")) > Long.parseLong(identifierEnd.replace(prefix, "")))
            throw new ValidationException("Identifier start should be less than identifier end");

        if (identifierSource.getType().equals(IdentifierGeneratorType.userBasedIdentifierGenerator) && assignedTo.getUserSettings().getIdPrefix() == null)
            throw new ValidationException("Id prefix is not assigned to the user");

        if (!(identifierStart.startsWith(prefix) && identifierEnd.startsWith(prefix)))
            throw new ValidationException("Both Identifier Start and End should match the prefix " + prefix);
    }

    @Override
    public String toString() {
        return "IdentifierUserAssignment{" +
                "identifierSource=" + identifierSource.getName() +
                ", assignedTo=" + assignedTo.getUsername() +
                ", identifierStart='" + identifierStart + '\'' +
                ", identifierEnd='" + identifierEnd + '\'' +
                '}';
    }
}

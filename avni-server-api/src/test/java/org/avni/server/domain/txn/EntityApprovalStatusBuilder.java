package org.avni.server.domain.txn;

import org.avni.server.domain.ApprovalStatus;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.Individual;
import org.joda.time.DateTime;

import java.util.UUID;

public class EntityApprovalStatusBuilder {
    private final EntityApprovalStatus entityApprovalStatus = new EntityApprovalStatus();

    public EntityApprovalStatusBuilder() {
        entityApprovalStatus.setUuid(UUID.randomUUID().toString());
        entityApprovalStatus.setAutoApproved(true);
    }

    public EntityApprovalStatusBuilder setIndividual(Individual individual) {
        entityApprovalStatus.setIndividual(individual);
        return this;
    }

    public EntityApprovalStatusBuilder setEntityType(EntityApprovalStatus.EntityType entityType) {
        entityApprovalStatus.setEntityType(entityType);
        return this;
    }

    public EntityApprovalStatusBuilder setApprovalStatus(ApprovalStatus approvalStatus) {
        entityApprovalStatus.setApprovalStatus(approvalStatus);
        return this;
    }

    public EntityApprovalStatusBuilder setApprovalStatusComment(String approvalStatusComment) {
        entityApprovalStatus.setApprovalStatusComment(approvalStatusComment);
        return this;
    }

    public EntityApprovalStatusBuilder setAutoApproved(Boolean autoApproved) {
        entityApprovalStatus.setAutoApproved(autoApproved);
        return this;
    }

    public EntityApprovalStatusBuilder setStatusDateTime(DateTime statusDateTime) {
        entityApprovalStatus.setStatusDateTime(statusDateTime);
        return this;
    }

    public EntityApprovalStatusBuilder setEntityTypeUuid(String entityTypeUuid) {
        entityApprovalStatus.setEntityTypeUuid(entityTypeUuid);
        return this;
    }

    public EntityApprovalStatusBuilder setEntityId(Long entityId) {
        entityApprovalStatus.setEntityId(entityId);
        return this;
    }

    public EntityApprovalStatus build() {
        return entityApprovalStatus;
    }
}

package org.avni.server.web.response;

import org.avni.server.domain.EntityApprovalStatus;

import java.util.LinkedHashMap;

public class EntityApprovalStatusResponse extends LinkedHashMap<String, Object> {
    public static EntityApprovalStatusResponse fromEntityApprovalStatus(EntityApprovalStatus entityApprovalStatus, String entityUuid) {
        EntityApprovalStatusResponse entityApprovalStatusResponse = new EntityApprovalStatusResponse();
        entityApprovalStatusResponse.put("Entity ID", entityUuid);
        entityApprovalStatusResponse.put("Entity type", entityApprovalStatus.getEntityType());
        entityApprovalStatusResponse.put("Entity type ID", entityApprovalStatus.getEntityTypeUuid());
        entityApprovalStatusResponse.put("Approval status", entityApprovalStatus.getApprovalStatus().getStatus());
        entityApprovalStatusResponse.put("Approval status comment", entityApprovalStatus.getApprovalStatusComment());
        entityApprovalStatusResponse.put("Status date time", entityApprovalStatus.getStatusDateTime());

        Response.putAudit(entityApprovalStatus, entityApprovalStatusResponse);
        return entityApprovalStatusResponse;
    }
}

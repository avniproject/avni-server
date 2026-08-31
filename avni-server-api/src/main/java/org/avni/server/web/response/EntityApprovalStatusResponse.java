package org.avni.server.web.response;

import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.service.ObservationService;

import java.util.LinkedHashMap;

public class EntityApprovalStatusResponse extends LinkedHashMap<String, Object> {
    public static EntityApprovalStatusResponse fromEntityApprovalStatus(EntityApprovalStatus entityApprovalStatus, String entityUuid, ObservationService observationService) {
        EntityApprovalStatusResponse entityApprovalStatusResponse = new EntityApprovalStatusResponse();
        entityApprovalStatusResponse.put("Entity ID", entityUuid);
        entityApprovalStatusResponse.put("Entity type", entityApprovalStatus.getEntityType());
        entityApprovalStatusResponse.put("Entity type ID", entityApprovalStatus.getEntityTypeUuid());
        entityApprovalStatusResponse.put("Approval status", entityApprovalStatus.getApprovalStatus().getStatus());
        entityApprovalStatusResponse.put("Approval status comment", entityApprovalStatus.getApprovalStatusComment());
        // A decision taken before an approval form was attached - and every decision in an organisation
        // that never attaches one - has a NULL observations column. constructObservations is @NotNull and
        // dereferences its argument straight away, so the null is caught here rather than becoming a 500
        // on data that predates this feature.
        ObservationCollection observations = entityApprovalStatus.getObservations();
        entityApprovalStatusResponse.put("observations",
                observations == null ? null : observationService.constructObservations(observations));
        entityApprovalStatusResponse.put("Status date time", entityApprovalStatus.getStatusDateTime());

        Response.putAudit(entityApprovalStatus, entityApprovalStatusResponse);
        return entityApprovalStatusResponse;
    }
}

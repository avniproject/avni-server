package org.avni.server.web.response;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.EntityApprovalStatus;
import org.avni.server.domain.ObservationCollection;
import org.avni.server.service.ConceptService;

import java.util.LinkedHashMap;

public class EntityApprovalStatusResponse extends LinkedHashMap<String, Object> {
    public static EntityApprovalStatusResponse fromEntityApprovalStatus(EntityApprovalStatus entityApprovalStatus, String entityUuid, ConceptRepository conceptRepository, ConceptService conceptService) {
        EntityApprovalStatusResponse entityApprovalStatusResponse = new EntityApprovalStatusResponse();
        entityApprovalStatusResponse.put("Entity ID", entityUuid);
        entityApprovalStatusResponse.put("Entity type", entityApprovalStatus.getEntityType());
        entityApprovalStatusResponse.put("Entity type ID", entityApprovalStatus.getEntityTypeUuid());
        entityApprovalStatusResponse.put("Approval status", entityApprovalStatus.getApprovalStatus().getStatus());
        entityApprovalStatusResponse.put("Approval status comment", entityApprovalStatus.getApprovalStatusComment());
        // A decision taken before an approval form was attached - and every decision in an organisation
        // that never attaches one - has a NULL observations column. Response.mapObservations would render
        // that as an empty map; the null is kept explicit here so those decisions come back the way the
        // story asks, distinguishable from a decision where the form was opened and left blank.
        ObservationCollection observations = entityApprovalStatus.getObservations();
        if (observations == null) {
            entityApprovalStatusResponse.put("observations", null);
        } else {
            // The same helper every other /api/ response uses, so an integration reading approvals gets the
            // shape it gets from /api/subjects: {question name: resolved answer name}, coded answers resolved
            // to concept names rather than left as UUIDs, and Dates normalised for the API version.
            Response.putObservations(conceptRepository, conceptService, entityApprovalStatusResponse, new LinkedHashMap<>(), observations);
        }
        entityApprovalStatusResponse.put("Status date time", entityApprovalStatus.getStatusDateTime());

        Response.putAudit(entityApprovalStatus, entityApprovalStatusResponse);
        return entityApprovalStatusResponse;
    }
}

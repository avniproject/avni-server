package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.individualRelationship.IndividualRelationship;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;

public class IndividualRelationshipResourceProcessor extends ResourceProcessor<IndividualRelationship>{
    @Override
    public EntityModel<IndividualRelationship> process(EntityModel<IndividualRelationship> resource) {
        IndividualRelationship individualRelationship = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(individualRelationship.getRelationship().getUuid(), "relationshipTypeUUID"));
        resource.add(Link.of(individualRelationship.getIndividuala().getUuid(), "individualAUUID"));
        resource.add(Link.of(individualRelationship.getIndividualB().getUuid(), "individualBUUID"));
        return resource;
    }
}

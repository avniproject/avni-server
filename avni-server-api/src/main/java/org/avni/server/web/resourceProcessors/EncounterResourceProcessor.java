package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.Encounter;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;

public class EncounterResourceProcessor extends ResourceProcessor<Encounter>{
    @Override
    public EntityModel<Encounter> process(EntityModel<Encounter> resource) {
        Encounter encounter = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(encounter.getEncounterType().getUuid(), "encounterTypeUUID"));
        resource.add(Link.of(encounter.getIndividual().getUuid(), "individualUUID"));
        return resource;
    }
}

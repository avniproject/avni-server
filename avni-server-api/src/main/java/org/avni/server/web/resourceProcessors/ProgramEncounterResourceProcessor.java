package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.ProgramEncounter;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;

public class ProgramEncounterResourceProcessor extends ResourceProcessor<ProgramEncounter> {
    @Override
    public EntityModel<ProgramEncounter> process(EntityModel<ProgramEncounter> resource) {
        ProgramEncounter programEncounter = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(programEncounter.getEncounterType().getUuid(), "encounterTypeUUID"));
        resource.add(Link.of(programEncounter.getProgramEnrolment().getUuid(), "programEnrolmentUUID"));
        return resource;
    }
}

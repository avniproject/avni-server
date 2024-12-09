package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.ProgramEnrolment;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;

public class ProgramEnrolmentResourceProcessor extends ResourceProcessor<ProgramEnrolment> {
    @Override
    public EntityModel<ProgramEnrolment> process(EntityModel<ProgramEnrolment> resource) {
        ProgramEnrolment programEnrolment = resource.getContent();
        resource.removeLinks();
        resource.add(Link.of(programEnrolment.getProgram().getUuid(), "programUUID"));
        resource.add(Link.of(programEnrolment.getIndividual().getUuid(), "individualUUID"));
        return resource;
    }
}

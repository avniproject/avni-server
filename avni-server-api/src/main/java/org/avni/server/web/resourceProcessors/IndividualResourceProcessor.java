package org.avni.server.web.resourceProcessors;

import org.avni.server.domain.Individual;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.Link;

public class IndividualResourceProcessor extends ResourceProcessor<Individual> {
    @Override
    public EntityModel<Individual> process(EntityModel<Individual> resource) {
        Individual individual = resource.getContent();
        resource.removeLinks();
        if (individual.getAddressLevel() != null) {
            resource.add(Link.of(individual.getAddressLevel().getUuid(), "addressUUID"));
        }
        if (individual.getGender() != null) {
            resource.add(Link.of(individual.getGender().getUuid(), "genderUUID"));
        }
        resource.add(Link.of(individual.getSubjectType().getUuid(), "subjectTypeUUID"));
        return resource;
    }
}

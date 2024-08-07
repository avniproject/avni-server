package org.avni.server.framework.rest;

import org.avni.server.domain.*;
import org.avni.server.domain.accessControl.GroupPrivilege;
import org.avni.server.domain.accessControl.Privilege;
import org.avni.server.domain.organisation.OrganisationCategory;
import org.avni.server.domain.organisation.OrganisationStatus;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.rest.core.config.RepositoryRestConfiguration;
import org.springframework.data.rest.webmvc.config.RepositoryRestConfigurerAdapter;

@Configuration
public class RepositoryConfig extends RepositoryRestConfigurerAdapter {
    @Override
    public void configureRepositoryRestConfiguration(RepositoryRestConfiguration config) {
        config.exposeIdsFor(User.class);
        config.exposeIdsFor(Organisation.class);
        config.exposeIdsFor(Catchment.class);
        config.exposeIdsFor(AddressLevel.class);
        config.exposeIdsFor(AddressLevelType.class);
        config.exposeIdsFor(Concept.class);
        config.exposeIdsFor(Program.class);
        config.exposeIdsFor(SubjectType.class);
        config.exposeIdsFor(EncounterType.class);
        config.exposeIdsFor(OrganisationConfig.class);
        config.exposeIdsFor(IdentifierSource.class);
        config.exposeIdsFor(IdentifierUserAssignment.class);
        config.exposeIdsFor(Account.class);
        config.exposeIdsFor(OrganisationGroup.class);
        config.exposeIdsFor(Group.class);
        config.exposeIdsFor(Privilege.class);
        config.exposeIdsFor(GroupPrivilege.class);
        config.exposeIdsFor(UserGroup.class);
        config.exposeIdsFor(GroupRole.class);
        config.exposeIdsFor(Card.class);
        config.exposeIdsFor(Dashboard.class);
        config.exposeIdsFor(News.class);
        config.exposeIdsFor(Comment.class);
        config.exposeIdsFor(CommentThread.class);
        config.exposeIdsFor(RuleFailureTelemetry.class);
        config.exposeIdsFor(Individual.class);
        config.exposeIdsFor(OrganisationCategory.class);
        config.exposeIdsFor(OrganisationStatus.class);

        //TODO
        /**
         * After we upgrade to a higher spring-boot version(2.1 and above), we would also have updated spring-data-repository plugin,
         * which would allow us to specify a simple 2-line configuration to not expose POST, PUT, PATCH and DELETE methods,
         * using ExposureConfiguration. After which, we could do away with following classes :
         * 1. AvniCrudRepository.java
         * 2. AvniJPARepository.java
         *
         * And remove overridden methods from CustomCHSJpaRepository.java
         *
         * Sample code snippet:
         *
         ExposureConfiguration config = restConfig.getExposureConfiguration();
         config.forDomainType(User.class).withItemExposure((metadata, httpMethods) ->
         httpMethods.disable(HttpMethod.PATCH));
         */
    }
}

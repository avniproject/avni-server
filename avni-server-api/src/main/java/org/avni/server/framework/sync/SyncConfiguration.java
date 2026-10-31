package org.avni.server.framework.sync;

import org.avni.server.framework.hibernate.DummyInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.handler.MappedInterceptor;

import java.util.stream.Stream;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SyncConfiguration implements WebMvcConfigurer {
    private final TransactionalResourceInterceptor transactionalResourceInterceptor;
    private final MetadataResourceInterceptor metadataResourceInterceptor;
    private final PageSizeGuardInterceptor pageSizeGuardInterceptor;
    private DummyInterceptor dummyInterceptor;

    private final String[] transactionalPathList = Stream.of(
            "checklist",
            "txNewChecklistEntity",
            "checklistItem",
            "txNewChecklistItemEntity",
            "encounter",
            "individual",
            "individualRelationship",
            "programEncounter",
            "programEnrolment",
            "videotelemetric",
            "identifierAssignmentPool",
            "api",
            "groupSubject",
            "entityApprovalStatus",
            "news",
            "comment",
            "commentThread",
            "resetSyncs"
    ).map(path-> "/" + path + "/**").toArray(String[]::new);

    private final String[] metadataPathList = Stream.of("addressLevel",
            "locations",
            "locationMapping",
            "catchment",
            "concept",
            "conceptAnswer",
            "encounterType",
            "form",
            "formElement",
            "formElementGroup",
            "formMapping",
            "gender",
            "individualRelation",
            "individualRelationGenderMapping",
            "individualRelationshipType",
            "operationalEncounterType",
            "operationalProgram",
            "program",
            "programConfig",
            "ruleDependency",
            "checklistDetail",
            "checklistItemDetail",
            "rule",
            "video",
            "userInfo",
            "me",
            "subjectType",
            "operationalSubjectType",
            "identifierSource",
            "organisationConfig",
            "translation",
            "platformTranslation",
            "groups",
            "myGroups",
            "groupPrivilege",
            "privilege",
            "groupRole",
            "locationHierarchy",
            "card",
            "dashboard",
            "dashboardCardMapping",
            "groupDashboard",
            "standardReportCardType",
            "approvalStatus",
            "dashboardSectionCardMapping",
            "dashboardSection",
            "dashboardFilter"
    ).map(path-> "/" + path + "/**").toArray(String[]::new);

    @Autowired
    public SyncConfiguration(TransactionalResourceInterceptor transactionalResourceInterceptor, MetadataResourceInterceptor metadataResourceInterceptor, PageSizeGuardInterceptor pageSizeGuardInterceptor, DummyInterceptor dummyInterceptor) {
        this.transactionalResourceInterceptor = transactionalResourceInterceptor;
        this.pageSizeGuardInterceptor = pageSizeGuardInterceptor;
        this.metadataResourceInterceptor = metadataResourceInterceptor;
        this.dummyInterceptor = dummyInterceptor;
    }

    @Bean("mappedTransactionalResourceInterceptor")
    public MappedInterceptor mappedTransactionalResourceInterceptor() {
        return new MappedInterceptor(transactionalPathList, transactionalResourceInterceptor);
    }

    @Bean("mappedMetadataResourceInterceptor")
    public MappedInterceptor mappedMetadataResourceInterceptor() {
        return new MappedInterceptor(metadataPathList, metadataResourceInterceptor);
    }

    /**
     * Every path, so it reaches the hand-written controllers and Spring Data REST's repository endpoints alike.
     * It acts only on handlers that take a Pageable.
     */
    @Bean("mappedPageSizeGuardInterceptor")
    public MappedInterceptor mappedPageSizeGuardInterceptor() {
        return new MappedInterceptor(new String[]{"/**"}, pageSizeGuardInterceptor);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dummyInterceptor);
    }
}

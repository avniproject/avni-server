package org.avni;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementGroup;
import org.avni.server.application.FormMapping;
import org.avni.server.dao.CustomJpaRepositoryImpl;
import org.avni.server.domain.*;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.domain.individualRelationship.IndividualRelationGenderMapping;
import org.avni.server.domain.individualRelationship.IndividualRelationshipType;
import org.avni.server.domain.task.TaskStatus;
import org.avni.server.importer.batch.JobService;
import org.avni.server.service.EntityApprovalStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelProcessor;

import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(repositoryBaseClass = CustomJpaRepositoryImpl.class)
public class Avni {
    private final JobService jobService;

    @Autowired
    public Avni(JobService jobService) {
        this.jobService = jobService;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(Avni.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<DashboardFilter>> DashboardFilterProcessor() {
        return new RepresentationModelProcessor<EntityModel<DashboardFilter>>() {
            @Override
            public EntityModel<DashboardFilter> process(EntityModel<DashboardFilter> resource) {
                DashboardFilter content = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(content.getDashboard().getUuid(), "dashboardUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<TaskStatus>> TaskStatusProcessor() {
        return new RepresentationModelProcessor<EntityModel<TaskStatus>>() {
            @Override
            public EntityModel<TaskStatus> process(EntityModel<TaskStatus> resource) {
                TaskStatus taskStatus = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(taskStatus.getTaskType().getUuid(), "taskTypeUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<DashboardSectionCardMapping>> DashboardSectionCardMappingProcessor() {
        return new RepresentationModelProcessor<EntityModel<DashboardSectionCardMapping>>() {
            @Override
            public EntityModel<DashboardSectionCardMapping> process(EntityModel<DashboardSectionCardMapping> resource) {
                DashboardSectionCardMapping dashboardSectionCardMapping = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(dashboardSectionCardMapping.getCard().getUuid(), "cardUUID"));
                resource.add(Link.of(dashboardSectionCardMapping.getDashboardSection().getUuid(), "dashboardSectionUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<OperationalSubjectType>> OperationalSubjectTypeProcessor() {
        return new RepresentationModelProcessor<EntityModel<OperationalSubjectType>>() {
            @Override
            public EntityModel<OperationalSubjectType> process(EntityModel<OperationalSubjectType> resource) {
                OperationalSubjectType operationalSubjectType = resource.getContent();
                resource.removeLinks();
                if (operationalSubjectType.getSubjectType().getSyncRegistrationConcept1() != null) {
                    resource.add(Link.of(operationalSubjectType.getSubjectType().getSyncRegistrationConcept1(), "syncRegistrationConcept1"));
                }
                if (operationalSubjectType.getSubjectType().getSyncRegistrationConcept2() != null) {
                    resource.add(Link.of(operationalSubjectType.getSubjectType().getSyncRegistrationConcept2(), "syncRegistrationConcept2"));
                }
                if (operationalSubjectType.getSubjectType().getNameHelpText() != null) {
                    resource.add(Link.of(operationalSubjectType.getSubjectType().getNameHelpText(), "nameHelpText"));
                }
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<GroupDashboard>> GroupDashboardProcessor() {
        return new RepresentationModelProcessor<EntityModel<GroupDashboard>>() {
            @Override
            public EntityModel<GroupDashboard> process(EntityModel<GroupDashboard> resource) {
                GroupDashboard groupDashboard = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(groupDashboard.getGroup().getUuid(), "groupUUID"));
                resource.add(Link.of(groupDashboard.getDashboard().getUuid(), "dashboardUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<EntityApprovalStatus>> EntityApprovalStatusProcessor() {
        return new RepresentationModelProcessor<EntityModel<EntityApprovalStatus>>() {
            @Autowired
            private EntityApprovalStatusService entityApprovalStatusService;

            @Override
            public EntityModel<EntityApprovalStatus> process(EntityModel<EntityApprovalStatus> resource) {
                EntityApprovalStatus entityApprovalStatus = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(entityApprovalStatusService.getEntityUuid(entityApprovalStatus), "entityUUID"));
                resource.add(Link.of(entityApprovalStatus.getApprovalStatus().getUuid(), "approvalStatusUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<ReportCard>> CardProcessor() {
        return new RepresentationModelProcessor<EntityModel<ReportCard>>() {
            @Override
            public EntityModel<ReportCard> process(EntityModel<ReportCard> resource) {
                ReportCard card = resource.getContent();
                StandardReportCardType standardReportCardType = card.getStandardReportCardType();
                resource.removeLinks();
                if (standardReportCardType != null) {
                    resource.add(Link.of(standardReportCardType.getUuid(), "standardReportCardUUID"));
                }
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<IndividualRelationshipType>> IndividualRelationshipTypeProcessor() {
        return new RepresentationModelProcessor<EntityModel<IndividualRelationshipType>>() {
            @Override
            public EntityModel<IndividualRelationshipType> process(EntityModel<IndividualRelationshipType> resource) {
                IndividualRelationshipType individualRelationshipType = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(individualRelationshipType.getIndividualAIsToB().getUuid(), "individualAIsToBRelationUUID"));
                resource.add(Link.of(individualRelationshipType.getIndividualBIsToA().getUuid(), "individualBIsToBRelationUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<IndividualRelationGenderMapping>> IndividualRelationGenderMappingProcessor() {
        return new RepresentationModelProcessor<EntityModel<IndividualRelationGenderMapping>>() {
            @Override
            public EntityModel<IndividualRelationGenderMapping> process(EntityModel<IndividualRelationGenderMapping> resource) {
                IndividualRelationGenderMapping individualRelationGenderMapping = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(individualRelationGenderMapping.getRelation().getUuid(), "relationUUID"));
                resource.add(Link.of(individualRelationGenderMapping.getGender().getUuid(), "genderUUID"));
                return resource;
            }
        };
    }


    @Bean
    public RepresentationModelProcessor<EntityModel<FormElement>> formElementProcessor() {
        return new RepresentationModelProcessor<EntityModel<FormElement>>() {
            @Override
            public EntityModel<FormElement> process(EntityModel<FormElement> resource) {
                FormElement formElement = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(formElement.getFormElementGroup().getUuid(), "formElementGroupUUID"));
                resource.add(Link.of(formElement.getConcept().getUuid(), "conceptUUID"));
                if (formElement.getGroup() != null) {
                    resource.add(Link.of(formElement.getGroup().getUuid(), "groupQuestionUUID"));
                }
                if (formElement.getDocumentation() != null) {
                    resource.add(Link.of(formElement.getDocumentation().getUuid(), "documentationUUID"));
                }
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<FormElementGroup>> formElementGroupProcessor() {
        return new RepresentationModelProcessor<EntityModel<FormElementGroup>>() {
            @Override
            public EntityModel<FormElementGroup> process(EntityModel<FormElementGroup> resource) {
                FormElementGroup formElementGroup = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(formElementGroup.getForm().getUuid(), "formUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<DocumentationItem>> documentationItemsProcessor() {
        return new RepresentationModelProcessor<EntityModel<DocumentationItem>>() {
            @Override
            public EntityModel<DocumentationItem> process(EntityModel<DocumentationItem> resource) {
                DocumentationItem documentationItem = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(documentationItem.getDocumentation().getUuid(), "documentationUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<ConceptAnswer>> conceptAnswerProcessor() {
        return new RepresentationModelProcessor<EntityModel<ConceptAnswer>>() {
            @Override
            public EntityModel<ConceptAnswer> process(EntityModel<ConceptAnswer> resource) {
                ConceptAnswer conceptAnswer = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(conceptAnswer.getConcept().getUuid(), "conceptUUID"));
                resource.add(Link.of(conceptAnswer.getAnswerConcept().getUuid(), "conceptAnswerUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<FormMapping>> FormMappingProcessor() {
        return new RepresentationModelProcessor<EntityModel<FormMapping>>() {
            @Override
            public EntityModel<FormMapping> process(EntityModel<FormMapping> resource) {
                FormMapping formMapping = resource.getContent();
                resource.removeLinks();
                Form form = formMapping.getForm();
                if (form != null) {
                    resource.add(Link.of(formMapping.getForm().getUuid(), "formUUID"));


                    String programUuid = formMapping.getProgramUuid();
                    if (programUuid != null) {
                        resource.add(Link.of(programUuid, "entityUUID"));
                    }

                    if (formMapping.getSubjectType() != null) {
                        resource.add(Link.of(formMapping.getSubjectType().getUuid(), "subjectTypeUUID"));
                    }
                    if (formMapping.getTaskType() != null) {
                        resource.add(Link.of(formMapping.getTaskTypeUuid(), "taskTypeUUID"));
                    }

                    String encounterTypeUuid = formMapping.getEncounterTypeUuid();
                    if (encounterTypeUuid != null) {
                        resource.add(Link.of(encounterTypeUuid, "observationsTypeEntityUUID"));
                    }

                    return resource;
                }
                return null;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<Rule>> RuleProcessor() {
        return new RepresentationModelProcessor<EntityModel<Rule>>() {
            @Override
            public EntityModel<Rule> process(EntityModel<Rule> resource) {
                Rule rule = resource.getContent();
                resource.removeLinks();
                RuledEntityType entityType = rule.getEntity().getType();
                String entityUUID = rule.getEntity().getUuid();
                String key = RuledEntityType.isForm(entityType) ? "formUUID"
                        : RuledEntityType.isProgram(entityType) ? "programUUID" : null;
                if (entityUUID != null && key != null) {
                    resource.add(Link.of(entityUUID, key));
                }
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<ChecklistItemDetail>> ChecklistItemDetailProcessor() {
        return new RepresentationModelProcessor<EntityModel<ChecklistItemDetail>>() {
            @Override
            public EntityModel<ChecklistItemDetail> process(EntityModel<ChecklistItemDetail> resource) {
                ChecklistItemDetail content = resource.getContent();
                resource.removeLinks();
                resource.add(Link.of(content.getChecklistDetail().getUuid(), "checklistDetailUUID"));
                resource.add(Link.of(content.getConcept().getUuid(), "conceptUUID"));
                resource.add(Link.of(content.getForm().getUuid(), "formUUID"));
                ChecklistItemDetail leadChecklistItemDetail = content.getLeadChecklistItemDetail();
                if (leadChecklistItemDetail != null) {
                    resource.add(Link.of(leadChecklistItemDetail.getUuid(), "leadDetailUUID"));
                }
                return resource;
            }
        };
    }

    @EventListener(ApplicationReadyEvent.class)
    public void restartFailedJobs() throws Exception {
        jobService.retryJobsFailedInLast2Hours();
    }
}

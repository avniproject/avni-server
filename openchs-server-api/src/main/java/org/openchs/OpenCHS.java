package org.openchs;

import org.openchs.application.Form;
import org.openchs.application.FormElement;
import org.openchs.application.FormElementGroup;
import org.openchs.application.FormMapping;
import org.openchs.domain.*;
import org.openchs.domain.individualRelationship.IndividualRelationGenderMapping;
import org.openchs.domain.individualRelationship.IndividualRelationshipType;
import org.openchs.importer.batch.JobService;
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

import java.util.stream.Collectors;

@SpringBootApplication
public class OpenCHS {
    private final JobService jobService;

    @Autowired
    public OpenCHS(JobService jobService) {
        this.jobService = jobService;
    }

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(OpenCHS.class);
        app.setWebApplicationType(WebApplicationType.SERVLET);
        app.run(args);
    }


    @Bean
    public RepresentationModelProcessor<EntityModel<IndividualRelationshipType>> IndividualRelationshipTypeProcessor() {
        return new RepresentationModelProcessor<EntityModel<IndividualRelationshipType>>() {
            @Override
            public EntityModel<IndividualRelationshipType> process(EntityModel<IndividualRelationshipType> resource) {
                IndividualRelationshipType individualRelationshipType = resource.getContent();
                resource.removeLinks();
                resource.add(new Link(individualRelationshipType.getIndividualAIsToB().getUuid(), "individualAIsToBRelationUUID"));
                resource.add(new Link(individualRelationshipType.getIndividualBIsToA().getUuid(), "individualBIsToBRelationUUID"));
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
                resource.add(new Link(individualRelationGenderMapping.getRelation().getUuid(), "relationUUID"));
                resource.add(new Link(individualRelationGenderMapping.getGender().getUuid(), "genderUUID"));
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
                resource.add(new Link(formElement.getFormElementGroup().getUuid(), "formElementGroupUUID"));
                resource.add(new Link(formElement.getConcept().getUuid(), "conceptUUID"));
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
                resource.add(new Link(formElementGroup.getForm().getUuid(), "formUUID"));
                return resource;
            }
        };
    }

    @Bean
    public RepresentationModelProcessor<EntityModel<ProgramOrganisationConfig>> programOrganisationConfig() {
        return new RepresentationModelProcessor<EntityModel<ProgramOrganisationConfig>>() {
            @Override
            public EntityModel<ProgramOrganisationConfig> process(EntityModel<ProgramOrganisationConfig> resource) {
                ProgramOrganisationConfig content = resource.getContent();
                resource.removeLinks();
                resource.add(new Link(content.getProgram().getUuid(), "programUUID"));
                String conceptUUIDs = content.getAtRiskConcepts().stream().map(CHSEntity::getUuid).collect(Collectors.joining(","));
                resource.add(new Link(conceptUUIDs, "conceptUUIDs"));
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
                resource.add(new Link(conceptAnswer.getConcept().getUuid(), "conceptUUID"));
                resource.add(new Link(conceptAnswer.getAnswerConcept().getUuid(), "conceptAnswerUUID"));
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
                if(form != null){
                    resource.add(new Link(formMapping.getForm().getUuid(), "formUUID"));


                    String programUuid = formMapping.getProgramUuid();
                    if (programUuid != null) {
                        resource.add(new Link(programUuid, "entityUUID"));
                    }

                    if (formMapping.getSubjectType() != null) {
                        resource.add(new Link(formMapping.getSubjectType().getUuid(), "subjectTypeUUID"));
                    }

                    String encounterTypeUuid = formMapping.getEncounterTypeUuid();
                    if (encounterTypeUuid != null) {
                        resource.add(new Link(encounterTypeUuid, "observationsTypeEntityUUID"));
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
                    resource.add(new Link(entityUUID, key));
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
                resource.add(new Link(content.getChecklistDetail().getUuid(), "checklistDetailUUID"));
                resource.add(new Link(content.getConcept().getUuid(), "conceptUUID"));
                resource.add(new Link(content.getForm().getUuid(), "formUUID"));
                ChecklistItemDetail leadChecklistItemDetail = content.getLeadChecklistItemDetail();
                if (leadChecklistItemDetail != null) {
                    resource.add(new Link(leadChecklistItemDetail.getUuid(), "leadDetailUUID"));
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

package org.avni.server.web;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementGroup;
import org.avni.server.application.FormMapping;
import org.avni.server.application.FormType;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.application.FormElementGroupRepository;
import org.avni.server.dao.application.FormElementRepository;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptAnswer;
import org.avni.server.framework.security.AuthenticationFilter;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.application.FormContract;
import org.avni.server.web.request.webapp.CreateUpdateFormRequest;
import org.hibernate.Hibernate;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Sql({"/test-data.sql"})
public class FormControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private FormMappingRepository formMappingRepository;

    @Autowired
    private ConceptRepository conceptRepository;

    @Autowired
    private FormElementRepository formElementRepository;

    @Autowired
    private FormRepository formRepository;

    @Autowired
    private FormController formController;

    @Autowired
    private FormElementGroupRepository formElementGroupRepository;

    private Object getJson(String path) throws IOException {
        return mapper.readValue(this.getClass().getResource(path), Object.class);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
        post("/programs", getJson("/ref/program.json"));
        post("/concepts/bulk", getJson("/ref/concepts.json"));
        post("/forms", getJson("/ref/forms/originalForm.json"));
    }

    @Test
    public void findByEntityId() {
        Page<FormMapping> fmPage = formMappingRepository.findByProgramId(1L, PageRequest.of(0, 1));
        assertEquals(1, fmPage.getContent().size());
    }

    @Test
    public void getForms() {
        ResponseEntity<String> response = template.getForEntity("/forms/program/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).startsWith("[{\"name\":\"encounter_form\",\"uuid\":\"2c32a184-6d27-4c51-841d-551ca94594a5\",\"formType\":\"ProgramEncounter\",\"programName\":\"Diabetes\"");
        assertThat(response.getBody()).contains("\"links\":[{\"rel\":\"self\"");
        assertThat(response.getBody()).contains("{\"rel\":\"form\"");
        assertThat(response.getBody()).contains("{\"rel\":\"formElementGroups\"");
        assertThat(response.getBody()).contains("{\"rel\":\"createdBy\"");
        assertThat(response.getBody()).contains("{\"rel\":\"lastModifiedBy\"");
    }

    /**
     * Form names had no uniqueness rule at any layer - no constraint, no entity annotation, no service or
     * controller check - while every sibling metadata entity refuses a repeat. Two forms in an organisation
     * could be named identically, leaving an administrator picking one from a list no way to tell them
     * apart.
     *
     * These go through the controller rather than over HTTP because the base class's post() helper asserts
     * a 2xx itself, so it cannot express a refusal.
     */
    private CreateUpdateFormRequest formRequest(String name) {
        CreateUpdateFormRequest request = new CreateUpdateFormRequest();
        request.setName(name);
        request.setFormType(FormType.IndividualProfile.name());
        // updateMetadata iterates getFormMappings() unguarded and the field has no default, so a request
        // without this throws a NullPointerException rather than saving.
        request.setFormMappings(new ArrayList<>());
        return request;
    }

    private void assertRefused(Runnable call, String expectedFragment) {
        try {
            call.run();
            org.junit.Assert.fail("Expected the form to be refused");
        } catch (BadRequestError e) {
            assertThat(e.getMessage()).contains(expectedFragment);
        }
    }

    @Test
    public void refusesASecondFormWithTheSameName() {
        formController.createWeb(formRequest("Household Registration"));

        assertRefused(() -> formController.createWeb(formRequest("Household Registration")),
                "already exists");
    }

    @Test
    public void namesTheFormThatIsAlreadyTaken() {
        formController.createWeb(formRequest("Household Registration"));

        assertRefused(() -> formController.createWeb(formRequest("Household Registration")),
                "Household Registration");
    }

    @Test
    public void ignoresCaseWhenComparingNames() {
        formController.createWeb(formRequest("Household Registration"));

        assertRefused(() -> formController.createWeb(formRequest("household registration")),
                "already exists");
    }

    @Test
    public void allowsTwoFormsWithDifferentNames() {
        formController.createWeb(formRequest("Household Registration"));
        formController.createWeb(formRequest("Member Registration"));

        assertThat(formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Member Registration")).hasSize(1);
    }

    /** Saving a form without touching its name must not be refused as a duplicate of itself. */
    @Test
    public void allowsSavingAFormWithoutChangingItsName() {
        formController.createWeb(formRequest("Household Registration"));
        Form form = formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Household Registration").get(0);

        formController.updateMetadata(formRequest("Household Registration"), form.getUuid());

        assertThat(formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Household Registration")).hasSize(1);
    }

    @Test
    public void refusesRenamingAFormOntoAnotherFormsName() {
        formController.createWeb(formRequest("Household Registration"));
        formController.createWeb(formRequest("Member Registration"));
        Form member = formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Member Registration").get(0);

        assertRefused(() -> formController.updateMetadata(formRequest("Household Registration"), member.getUuid()),
                "already exists");
    }

    /**
     * Voiding renames a form to "<name> (voided~<id>)" precisely so the name can be used again, so a voided
     * form must not hold its old name hostage.
     */
    @Test
    public void allowsReusingTheNameOfAVoidedForm() {
        formController.createWeb(formRequest("Household Registration"));
        Form form = formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Household Registration").get(0);
        form.setVoided(true);
        formRepository.save(form);

        formController.createWeb(formRequest("Household Registration"));

        assertThat(formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Household Registration")).hasSize(1);
    }

    /**
     * The rule must not turn the duplicates that already exist into unsaveable forms.
     *
     * 224 live forms across 57 production organisations share a name with another form, from before there
     * was anything stopping it. Looking the name up as a single result raises
     * IncorrectResultSizeDataAccessException on those rows, or returns whichever of the pair it likes -
     * so saving one of them, even for an edit that has nothing to do with its name, would fail.
     */
    @Test
    public void allowsSavingAFormWhoseNameWasAlreadyDuplicated() {
        formController.createWeb(formRequest("Shared Name"));
        Form first = formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Shared Name").get(0);
        formController.createWeb(formRequest("Set Aside"));
        Form second = formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Set Aside").get(0);
        // Renamed straight through the repository: the controller is what refuses this, and the point is to
        // reproduce data that predates the rule.
        second.setName("Shared Name");
        formRepository.save(second);

        formController.updateMetadata(formRequest("Shared Name"), first.getUuid());

        assertThat(formRepository.findByNameIgnoreCaseAndIsVoidedFalse("Shared Name")).hasSize(2);
    }

    @Test
    @Ignore("Not Applicable as coded-concepts are created/updated by concept API")
    public void renameOfAnswersViaFormElements() throws IOException {
        ResponseEntity<FormContract> formResponse = template
                .getForEntity(String.format("/forms/export?formUUID=%s", "0c444bf3-54c3-41e4-8ca9-f0deb8760831"),
                        FormContract.class);
        assertEquals(HttpStatus.OK, formResponse.getStatusCode());
        FormContract form = formResponse.getBody();
        List<ConceptContract> answers = form.getFormElementGroups().get(0).getFormElements().get(0).getConcept().getAnswers();
        for (ConceptContract answer : answers) {
            if (answer.getUuid().equals("28e76608-dddd-4914-bd44-3689eccfa5ca")) {
                assertEquals("Yes, started", answer.getName());
            }
        }
        post("/forms", getJson("/ref/forms/formWithRenamedAnswer.json"));
        formResponse = template
                .getForEntity(String.format("/forms/export?formUUID=%s", "0c444bf3-54c3-41e4-8ca9-f0deb8760831"),
                        FormContract.class);
        assertEquals(HttpStatus.OK, formResponse.getStatusCode());
        form = formResponse.getBody();
        answers = form.getFormElementGroups().get(0).getFormElements().get(0).getConcept().getAnswers();
        for (ConceptContract answer : answers) {
            if (answer.getUuid().equals("28e76608-dddd-4914-bd44-3689eccfa5ca")) {
                assertEquals("Yes Started", answer.getName());
            }
        }
    }

    @Test
    @Ignore("Not Applicable as coded-concepts are created/updated by concept API")
    public void deleteExistingAnswerInFormElement() throws IOException {
        post("/forms", getJson("/ref/forms/originalForm.json"));

        Concept conceptWithAnswerToBeDeleted = conceptRepository.findByName("Have you started going to school once again");
        assertThat(conceptWithAnswerToBeDeleted.getConceptAnswers().size()).isEqualTo(3);
        ConceptAnswer answerToBeDeleted = conceptWithAnswerToBeDeleted.findConceptAnswerByName("Yes, started");
        assertThat(answerToBeDeleted).isNotNull();
        assertThat(answerToBeDeleted.isVoided()).isFalse();


        post("/forms", getJson("/ref/forms/formWithDeletedAnswer.json"));

        Concept conceptWithDeletedAnswer = conceptRepository.findByName("Have you started going to school once again");
        assertThat(conceptWithDeletedAnswer.getConceptAnswers().size()).isEqualTo(3);
        ConceptAnswer answerDeleted = conceptWithDeletedAnswer.findConceptAnswerByName("Yes, started");
        assertThat(answerDeleted).isNotNull();
        assertThat(answerDeleted.isVoided()).isTrue();
    }

    @Test
    public void deleteFormElement() throws IOException {
        post("/forms", getJson("/ref/forms/originalForm.json"));

        assertThat(formElementRepository.findByUuid("45a1595c-c324-4e76-b8cd-0873e465b5ae")).isNotNull();

        post("/forms", getJson("/ref/forms/formWithDeletedFormElement.json"));

        assertThat(formElementRepository.findByUuid("45a1595c-c324-4e76-b8cd-0873e465b5ae").isVoided()).isTrue();
    }


    @Test
    public void deleteFormElementGroup() throws IOException {
        post("/forms", getJson("/ref/forms/originalForm.json"));
        assertThat(formElementGroupRepository.findByUuid("e47c7604-6cb6-45bb-a36a-05a03f958cdf")).isNotNull();


        post("/forms", getJson("/ref/forms/formWithDeletedFormElementGroup.json"));
        assertThat(formElementGroupRepository.findByUuid("e47c7604-6cb6-45bb-a36a-05a03f958cdf").isVoided()).isTrue();
    }

    @Test
    @Ignore("Not Applicable as coded-concepts are created/updated by concept API")
    public void changingOrderOfAllAnswers() throws IOException {
        Concept codedConcept = conceptRepository.findByUuid("dcfc771a-0785-43be-bcb1-0d2755793e0e");
        Set<ConceptAnswer> conceptAnswers = codedConcept.getConceptAnswers();
        conceptAnswers.forEach(conceptAnswer -> {
            switch ((int) conceptAnswer.getOrder()) {
                case 1:
                    assertEquals("28e76608-dddd-4914-bd44-3689eccfa5ca", conceptAnswer.getAnswerConcept().getUuid());
                    break;
                case 2:
                    assertEquals("9715936e-03f2-44da-900f-33588fe95250", conceptAnswer.getAnswerConcept().getUuid());
                    break;
                case 3:
                    assertEquals("e7b50c78-3d90-484d-a224-9887887780dc", conceptAnswer.getAnswerConcept().getUuid());
                    break;
            }

        });
        post("/forms", getJson("/ref/forms/formWithChangedAnswerOrder.json"));
        codedConcept = conceptRepository.findByUuid("dcfc771a-0785-43be-bcb1-0d2755793e0e");
        conceptAnswers = codedConcept.getConceptAnswers();
        conceptAnswers.forEach(conceptAnswer -> {
            switch ((int) conceptAnswer.getOrder()) {
                case 1:
                    assertEquals("9715936e-03f2-44da-900f-33588fe95250", conceptAnswer.getAnswerConcept().getUuid());
                    break;
                case 2:
                    assertEquals("28e76608-dddd-4914-bd44-3689eccfa5ca", conceptAnswer.getAnswerConcept().getUuid());
                    break;
                case 3:
                    assertEquals("e7b50c78-3d90-484d-a224-9887887780dc", conceptAnswer.getAnswerConcept().getUuid());
                    break;
            }

        });
    }

    @Test
    public void abilityToAddFormElementsForOnlyAnOrganisationToAnExistingFormElementGroup() throws IOException {
        post("/forms", getJson("/ref/demo/originalForm.json"));
        FormElementGroup formElementGroup = formElementGroupRepository.findByUuid("dd37cacf-c628-457e-b474-01c4966a473c");
        Hibernate.initialize(formElementGroup.getFormElements());
        assertEquals(2, formElementGroup.getFormElements().size());
        template.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add(AuthenticationFilter.USER_NAME_HEADER, "demo-admin");
                    return execution.execute(request, body);
                }));
        template.patchForObject("/forms", getJson("/ref/demo/originalFormChanges.json"), Void.class, new Object());
        formElementGroup = formElementGroupRepository.findByUuid("dd37cacf-c628-457e-b474-01c4966a473c");
        assertEquals(3, formElementGroup.getFormElements().size());
        List<FormElement> formElements = new ArrayList<>(formElementGroup.getFormElements());
        formElements.sort(Comparator.comparingDouble(FormElement::getDisplayOrder));
        FormElement addedFormElement = formElements.get(1);
        assertEquals("Additional Form Element", addedFormElement.getName());
        assertEquals("836ceda5-3d09-49f6-aa0b-28512bc2028a", addedFormElement.getUuid());
        assertEquals(1.1, addedFormElement.getDisplayOrder().doubleValue(), 0);
        assertEquals("43124c33-898d-42fa-a53d-b4c6fa36c581", addedFormElement.getConcept().getUuid());
        assertEquals(2, addedFormElement.getOrganisationId().intValue());
    }

    @Test
    public void abilityToAddFormElementGroupForOnlyAnOrganisationToAnExistingFormElementGroup() throws IOException {
        post("/forms", getJson("/ref/demo/originalForm.json"));
        Form form = formRepository.findByUuid("0c444bf3-54c3-41e4-8ca9-f0deb8760831");
        Hibernate.initialize(form.getFormElementGroups());
        assertEquals(2, form.getFormElementGroups().size());
        template.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add(AuthenticationFilter.USER_NAME_HEADER, "demo-admin");
                    return execution.execute(request, body);
                }));
        template.patchForObject("/forms", getJson("/ref/demo/originalNewFormElementGroup.json"), Void.class, new Object());
        form = formRepository.findByUuid("0c444bf3-54c3-41e4-8ca9-f0deb8760831");
        assertEquals(3, form.getFormElementGroups().size());
        ArrayList<FormElementGroup> formElementGroups = new ArrayList<>(form.getFormElementGroups());
        formElementGroups.sort(Comparator.comparingDouble(FormElementGroup::getDisplayOrder));
        FormElementGroup formElementGroup = formElementGroups.get(1);
        assertEquals("Additional Form Element Group", formElementGroup.getName());
        assertEquals("118a1605-c7df-4942-9666-4c17af585d24", formElementGroup.getUuid());
        assertEquals(1.1, formElementGroup.getDisplayOrder().doubleValue(), 0);
        assertEquals(2, formElementGroup.getOrganisationId().intValue());
    }

    @Test
    @Ignore
    public void getAllApplicableFormElementGroupsAndFormElementsForAnOrganisation() throws IOException {
        post("/forms", getJson("/ref/demo/originalForm.json"));
        Form form = formRepository.findByUuid("0c444bf3-54c3-41e4-8ca9-f0deb8760831");
        Hibernate.initialize(form.getFormElementGroups());
        assertEquals(2, form.getFormElementGroups().size());
        template.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add(AuthenticationFilter.USER_NAME_HEADER, "admin");
                    return execution.execute(request, body);
                }));
        template.patchForObject("/forms", getJson("/ref/demo/originalNewFormElementGroup.json"), Void.class, new Object());
        form = formRepository.findByUuid("0c444bf3-54c3-41e4-8ca9-f0deb8760831");
        assertEquals(3, form.getFormElementGroups().size());
        ArrayList<FormElementGroup> formElementGroups = new ArrayList<>(form.getFormElementGroups());
        formElementGroups.sort(Comparator.comparingDouble(FormElementGroup::getDisplayOrder));
        FormElementGroup formElementGroup = formElementGroups.get(1);
        assertEquals("Additional Form Element Group", formElementGroup.getName());
        assertEquals("118a1605-c7df-4942-9666-4c17af585d24", formElementGroup.getUuid());
        assertEquals(1.1, formElementGroup.getDisplayOrder().doubleValue(), 0);
        assertEquals(2, formElementGroup.getOrganisationId().intValue());
        MultiValueMap<String, String> uriParams = new LinkedMultiValueMap<>();
        uriParams.put("catchmentId", Arrays.asList("1"));
        uriParams.put("lastModifiedDateTime", Arrays.asList("1900-01-01T00:00:00.001Z"));
        uriParams.put("size", Arrays.asList("100"));
        uriParams.put("page", Arrays.asList("0"));
        String path = UriComponentsBuilder.fromPath("/form/search/lastModified").queryParams(uriParams).toUriString();
        ResponseEntity<LinkedHashMap> formResponse = template.getForEntity(path, LinkedHashMap.class, uriParams);
        assertEquals(HttpStatus.OK, formResponse.getStatusCode());
        LinkedHashMap<String, LinkedHashMap<String, List<LinkedHashMap<String, List>>>> formBody = formResponse.getBody();
        assertEquals(4, formBody.get("_embedded").get("form").get(0).get("allFormElements").size());
        template.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add(AuthenticationFilter.USER_NAME_HEADER, "a-admin");
                    return execution.execute(request, body);
                }));
        formResponse = template.getForEntity(path, LinkedHashMap.class, uriParams);
        assertEquals(HttpStatus.OK, formResponse.getStatusCode());
        assertEquals(3, formBody.get("_embedded").get("form").get(0).get("allFormElements").size());
    }

    @Test
    public void shouldRequireConceptsPreCreatedWhenCreatingFormElement() throws IOException {
        Object json = getJson("/ref/forms/formElementWithConceptDefinedAlong.json");
        ResponseEntity<String> responseEntity = this.template.postForEntity("/forms", json, String.class);
        assertThat(responseEntity.getStatusCode().is4xxClientError()).isTrue();
        assertThat(responseEntity.getBody()).contains("Concept with uuid '79604c31-4fa5-4300-a460-9328d8b6217e' not found");
    }


    @Test
    @Ignore("This is not what the code says. Not sure why we would have this test. ")
    public void shouldNotUpdateAuditInfoWhenFormUploadedTwiceWithSameKeyValues() throws IOException {
        String formUuid = "0c444bf3-54c3-41e4-8ca9-f0deb8760831";
        Object formJson = getJson("/ref/forms/formWithKeyValues.json");

        post("/forms", formJson);
        Form originalForm = formRepository.findByUuid(formUuid);
        FormElementGroup originalGroup = formElementGroupRepository.findByUuid("dd37cacf-c628-457e-b474-01c4966a473c");
        FormElement originalFormElement = formElementRepository.findByUuid("6e200310-4948-47dc-a573-411aaafd199d");

        post("/forms", formJson);
        Form updatedForm = formRepository.findByUuid(formUuid);
        FormElementGroup updatedGroup = formElementGroupRepository.findByUuid("dd37cacf-c628-457e-b474-01c4966a473c");
        FormElement updatedFormElement = formElementRepository.findByUuid("6e200310-4948-47dc-a573-411aaafd199d");

        assertThat(originalForm).isNotNull();
        assertThat(updatedForm).isNotNull();
        assertThat(updatedForm.getLastModifiedDateTime()).isEqualByComparingTo(originalForm.getLastModifiedDateTime());

        assertThat(originalGroup).isNotNull();
        assertThat(updatedGroup).isNotNull();
        assertThat(updatedGroup.getLastModifiedDateTime()).isEqualByComparingTo(originalGroup.getLastModifiedDateTime());

        assertThat(originalFormElement).isNotNull();
        assertThat(updatedFormElement).isNotNull();
        assertThat(updatedFormElement.getLastModifiedDateTime()).isEqualByComparingTo(originalFormElement.getLastModifiedDateTime());

    }

    @Test
    public void shouldRetrieveFormForWeb() throws Exception {
        setUser("demo-user");
        String formUuid = "0c444bf3-54c3-41e4-8ca9-f0deb8760831";
        mockMvc.perform(get("/web/form/{formUuid}", formUuid))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldHaveAllRelevantFieldsInResponse() throws Exception {
        setUser("demo-user");
        String formUuid = "0c444bf3-54c3-41e4-8ca9-f0deb8760831";
        String firstFormElementGroup = "$.formElementGroups[?(@.name == 'School Going Details')]";
        String formElement = String.format("%s.applicableFormElements[?(@.name == 'Have you started going to school once again')]", firstFormElementGroup);
        String concept = String.format("%s.concept", formElement);
        String conceptAnswer = String.format("%s.conceptAnswers[?(@.answerConcept.name == 'No')].answerConcept", concept);

        mockMvc.perform(get("/web/form/{formUuid}", formUuid)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())

//                .andDo(print())

                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.uuid").exists())
                .andExpect(jsonPath("$.formType").exists())
                .andExpect(jsonPath("$.formElementGroups").isArray())
                .andExpect(jsonPath(firstFormElementGroup).exists())

                .andExpect(jsonPath(formElement).exists())
                .andExpect(jsonPath(String.format("%s.name", formElement)).exists())
                .andExpect(jsonPath(String.format("%s.uuid", formElement)).exists())
                .andExpect(jsonPath(String.format("%s.keyValues", formElement)).exists())
                .andExpect(jsonPath(String.format("%s.type", formElement)).exists())

                .andExpect(jsonPath(concept).exists())
                .andExpect(jsonPath(String.format("%s.dataType", concept)).exists())
                .andExpect(jsonPath(String.format("%s.lowAbsolute", concept)).exists())
                .andExpect(jsonPath(String.format("%s.highAbsolute", concept)).exists())
                .andExpect(jsonPath(String.format("%s.lowNormal", concept)).exists())
                .andExpect(jsonPath(String.format("%s.highNormal", concept)).exists())
                .andExpect(jsonPath(String.format("%s.conceptAnswers", concept)).isArray())

                .andExpect(jsonPath(conceptAnswer).exists())
                .andExpect(jsonPath(String.format("%s.name", conceptAnswer)).exists());
    }

    @Test
    public void shouldNotIncludeNonApplicableFormElementsInResponse() throws Exception {
        setUser("demo-admin");
        String content = mapper.writeValueAsString(getJson("/ref/forms/formWithANonApplicableElement.json"));
        mockMvc.perform(request(HttpMethod.DELETE, "/forms").contentType(MediaType.APPLICATION_JSON).content(content))
        .andExpect(status().isOk());

        setUser("demo-user");
        String formUuid = "0c444bf3-54c3-41e4-8ca9-f0deb8760831";
        mockMvc.perform(get("/web/form/{formUuid}", formUuid)
                .accept(MediaType.APPLICATION_JSON_VALUE)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$..[?(@.name == 'Another random numeric question')]").doesNotExist());
    }
}

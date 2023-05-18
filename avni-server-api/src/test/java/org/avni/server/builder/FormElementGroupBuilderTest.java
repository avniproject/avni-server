package org.avni.server.builder;

import org.avni.server.application.Form;
import org.avni.server.application.FormElement;
import org.avni.server.application.FormElementGroup;
import org.avni.server.application.FormType;
import org.avni.server.domain.Concept;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.domain.factory.metadata.FormElementBuilder;
import org.avni.server.service.ConceptService;
import org.avni.server.service.DocumentationService;
import org.avni.server.web.request.ConceptContract;
import org.avni.server.web.request.application.FormElementContract;
import org.avni.server.web.request.application.FormElementGroupContract;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static junit.framework.TestCase.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

public class FormElementGroupBuilderTest {
    @Test
    public void voidedFormElementGroupShouldVoidAllFormElements() throws FormBuilderException {
        ConceptService conceptService = mock(ConceptService.class);
        DocumentationService documentationService = mock(DocumentationService.class);

        FormElementGroupContract formElementGroupContract = new FormElementGroupContract();
        FormElementContract formElementContract = new FormElementContract();
        formElementContract.setUuid("fe-uuid-1");
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid("c-uuid-1");
        formElementContract.setConcept(conceptContract);
        formElementGroupContract.addFormElement(formElementContract);
        formElementContract.setVoided(true);


        Concept concept = new ConceptBuilder().withUuid("c-uuid-1").build();
        FormElement existingFormElement = new FormElementBuilder().withUuid("fe-uuid-1").withConcept(concept).build();
        FormElementGroup existingFormElementGroup = new FormElementGroup();
        existingFormElementGroup.addFormElement(existingFormElement);
        FormElementGroupBuilder formElementGroupBuilder = new FormElementGroupBuilder(new Form(), existingFormElementGroup, new FormElementGroup(), conceptService, documentationService);
        FormElementGroup updatedFormElementGroup = formElementGroupBuilder.makeFormElements(formElementGroupContract).build();

        updatedFormElementGroup.getFormElements().forEach(formElement -> {
            assertTrue(formElement.isVoided());
            assertFalse(formElement.getConcept().isVoided());
        });
    }

    @Test
    public void updateDisplayOrderForAllFEGinForm() throws FormBuilderException {
        Concept concept = new ConceptBuilder().withUuid("c-uuid-1").build();
        ConceptService conceptService = mock(ConceptService.class);
        Mockito.when(conceptService.get(anyString())).thenReturn(concept);
        DocumentationService documentationService = mock(DocumentationService.class);
        FormElementGroupContract secondVoided = initFEGWithFEAndConcept(2d, true);
        FormElementGroupContract firstUnVoided = initFEGWithFEAndConcept(1d, false);
        FormElementGroupContract secondUnVoided = initFEGWithFEAndConcept(2d, false);
        FormElementGroupContract thirdUnVoided = initFEGWithFEAndConcept(3d, false);
        List<FormElementGroupContract> formElementGroups = Arrays.asList(secondVoided, firstUnVoided, secondUnVoided, thirdUnVoided);

        FormBuilder formBuilder = new FormBuilder(null);
        Form form = formBuilder.withName("formName")
                .withType(FormType.Encounter.name())
                .withUUID("form-uuid-1")
                .withFormElementGroups(formElementGroups, conceptService, documentationService)
                .withVoided(false)
                .build();
        /**
         * Test for formElementGroups validity
         */
        AtomicInteger count=new AtomicInteger(0);
        assertEquals(formElementGroups.size(), form.getFormElementGroups().size());
        form.getFormElementGroups().stream().sorted(Comparator.comparing(FormElementGroup::getDisplayOrder))
                .forEach(formElementGroup -> {
                    assertTrue(formElementGroup.getDisplayOrder() == (double) count.incrementAndGet());
                });

        /**
         * Test for formElements validity
         */
        AtomicInteger feCount=new AtomicInteger(0);
        assertEquals(formElementGroups.get(0).getFormElements().size(), form.getFormElementGroups().stream().findFirst().get().getFormElements().size());
        form.getFormElementGroups().stream().findFirst().get().getFormElements().stream()
                .sorted(Comparator.comparing(FormElement::getDisplayOrder))
                .forEach(formElement -> {
                    assertTrue(formElement.getDisplayOrder() == (double) feCount.incrementAndGet());
                });
    }

    private FormElementGroupContract initFEGWithFEAndConcept(double displayOrder, boolean voided) {
        FormElementGroupContract formElementGroupContract = new FormElementGroupContract();
        String uuid = "feg-uuid-" + displayOrder + voided;
        formElementGroupContract.setUuid(uuid);
        formElementGroupContract.setName(uuid);
        formElementGroupContract.setDisplayOrder(displayOrder);
        initFE(displayOrder, 2, false, formElementGroupContract);
        initFE(displayOrder, 3, false, formElementGroupContract);
        initFE(displayOrder, 1, false, formElementGroupContract);
        initFE(displayOrder, 1, true, formElementGroupContract);
        return formElementGroupContract;
    }

    private void initFE(double fegDisplayOrder, double feDisplayOrder, boolean voided, FormElementGroupContract formElementGroupContract) {
        FormElementContract formElementContract = new FormElementContract();
        String uuid = fegDisplayOrder + "fe-uuid-" + feDisplayOrder + voided;
        formElementContract.setUuid(uuid);
        formElementContract.setName(uuid);
        formElementContract.setDisplayOrder(feDisplayOrder);
        ConceptContract conceptContract = new ConceptContract();
        conceptContract.setUuid(fegDisplayOrder+"c-uuid-"+ feDisplayOrder + voided);
        formElementContract.setConcept(conceptContract);
        formElementGroupContract.addFormElement(formElementContract);
        formElementContract.setVoided(voided);
    }
}

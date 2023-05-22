package org.avni.exporter.v2;

import org.avni.server.application.FormElement;
import org.avni.server.application.Subject;
import org.avni.server.application.TestFormElementBuilder;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.SubjectType;
import org.avni.server.domain.factory.metadata.ConceptBuilder;
import org.avni.server.exporter.v2.HeaderCreator;
import org.avni.server.web.external.request.export.ExportEntityType;
import org.avni.server.web.request.ExportEntityTypeBuilder;
import org.junit.Ignore;
import org.junit.Test;

import java.util.*;

import static org.avni.server.exporter.v2.LongitudinalExportRequestFieldNameConstants.*;
import static org.junit.Assert.assertEquals;

public class HeaderCreatorTest {
    @Test
    public void shouldAddOnlyPassedRegistrationHeaders() {
        SubjectType subjectType = getSubjectType("Individual", Subject.Person);

        HeaderCreator headerCreator = new HeaderCreator();
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUserProvidedFields(Arrays.asList(ID, UUID, FIRST_NAME, CREATED_BY, CREATED_DATE_TIME, LAST_MODIFIED_BY, LAST_MODIFIED_DATE_TIME)).build();
        List<String> addressLevelTypes = Arrays.asList("Village");
        Map<String, FormElement> registrationMap = getStringFormElementMap();
        StringBuilder registrationHeaders = headerCreator.addRegistrationHeaders(subjectType, registrationMap, addressLevelTypes, exportEntityType);
        assertEquals(registrationHeaders.toString(), "Individual_id,Individual_uuid,Individual_first_name,Individual_created_by,Individual_created_date_time,Individual_last_modified_by,Individual_last_modified_date_time,\"Village\",\"Individual_Question 1\"");
    }

    @Test
    @Ignore
    public void shouldAddQuestionGroupHeaders() {
        SubjectType subjectType = getSubjectType("Individual", Subject.Person);
        HeaderCreator headerCreator = new HeaderCreator();
        List<String> addressLevelTypes = Collections.singletonList("Village");

        Concept groupConcept = new ConceptBuilder().withName("GC").withDataType(ConceptDataType.QuestionGroup).withUuid("gc").build();
        Concept memberConcept1 = new ConceptBuilder().withName("MC1").withDataType(ConceptDataType.Text).withUuid("mc1").build();
        Concept memberConcept2 = new ConceptBuilder().withName("MC2").withDataType(ConceptDataType.Text).withUuid("mc2").build();
        FormElement groupFormElement = new TestFormElementBuilder().withConcept(groupConcept).build();
        FormElement formElement1 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept1).build();
        FormElement formElement2 = new TestFormElementBuilder().withQuestionGroupElement(groupFormElement).withConcept(memberConcept2).build();
        LinkedHashMap<String, FormElement> formElementsMap = new LinkedHashMap<String, FormElement>() {{
            put("gc", groupFormElement);
            put("c1", formElement1);
            put("c2", formElement2);
        }};
        StringBuilder registrationHeaders = headerCreator.addRegistrationHeaders(subjectType, formElementsMap, addressLevelTypes, null);
    }

    @Test
    public void shouldAddAllStaticHeadersIfNoFieldsIsPassed() {
        SubjectType subjectType = getSubjectType("ABC", Subject.Person);
        List<String> addressLevelTypes = Arrays.asList("Village");
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().build();
        HeaderCreator headerCreator = new HeaderCreator();
        StringBuilder registrationHeaders = headerCreator.addRegistrationHeaders(subjectType, new HashMap<>(), addressLevelTypes, exportEntityType);
        assertEquals(registrationHeaders.toString(), "ABC_id,ABC_uuid,ABC_first_name,ABC_middle_name,ABC_last_name,ABC_date_of_birth,ABC_registration_date,ABC_gender,ABC_created_by,ABC_created_date_time,ABC_last_modified_by,ABC_last_modified_date_time,ABC_voided,\"Village\"");
    }

    @Test
    public void shouldGenerateMultipleColumnsForEncounter() {
        HeaderCreator headerCreator = new HeaderCreator();
        ExportEntityType exportEntityType = new ExportEntityTypeBuilder().withUserProvidedFields(Arrays.asList(ID, CREATED_BY, CREATED_DATE_TIME, LAST_MODIFIED_BY, LAST_MODIFIED_DATE_TIME)).build();
        StringBuilder stringBuilder = headerCreator.addEncounterHeaders(2L, getStringFormElementMap(), getStringFormElementMap(), "ENC", exportEntityType);
        assertEquals("ENC_1_id,ENC_1_created_by,ENC_1_created_date_time,ENC_1_last_modified_by,ENC_1_last_modified_date_time,\"ENC_1_Question 1\",\"ENC_1_Question 1\",ENC_2_id,ENC_2_created_by,ENC_2_created_date_time,ENC_2_last_modified_by,ENC_2_last_modified_date_time,\"ENC_2_Question 1\",\"ENC_2_Question 1\"", stringBuilder.toString());
    }

    private SubjectType getSubjectType(String name, Subject type) {
        SubjectType subjectType = new SubjectType();
        subjectType.setName(name);
        subjectType.setUuid("1890a382-8f47-4d2c-9126-40cb38a22e1f");
        subjectType.setId(200L);
        subjectType.setType(type);
        return subjectType;
    }

    private Map<String, FormElement> getStringFormElementMap() {
        Map<String, FormElement> map = new HashMap<>();
        Concept concept = new Concept();
        FormElement formElement = new FormElement();
        formElement.setType("SingleSelect");
        formElement.setConcept(concept);
        concept.setDataType(ConceptDataType.Text.toString());
        concept.setName("Question 1");
        map.put("Question 1 uuid", formElement);
        return map;
    }
}

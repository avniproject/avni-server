package org.avni.server.web;

import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ConceptControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ConceptRepository conceptRepository;

    private void post(Object json) {
        super.post("/concepts/bulk", json);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void shouldCreateConcepts() throws IOException {
        Object json = getJSON("/ref/concepts/concepts.json");
        post(json);

        Concept naConcept = conceptRepository.findByUuid("b82d4ed8-6e9f-4c67-bfdc-b1a04861bc20");
        assertThat(naConcept).isNotNull();
        assertThat(naConcept.getName()).isEqualTo("NA concept");
    }

    @Test
    public void shouldCreateConceptsWithOneLevelNesting() throws IOException {
        Object json = getJSON("/ref/concepts/codedConceptsWithOneLevelNesting.json");
        post(json);

        Concept nestedConcept = conceptRepository.findByUuid("0ca1c6a2-001b-475a-9813-1d905df9e81b");
        assertThat(nestedConcept).isNotNull();
        assertThat(nestedConcept.getName()).isEqualTo("High Risk Conditions");
    }

    @Test
    public void shouldFailToCreateConceptsWithMultipleNesting() throws IOException {
        Object json = getJSON("/ref/concepts/codedConceptsWithMultipleNesting.json");
        post(json);
    }

    @Test
    public void shouldVoidAConcept() throws IOException {
        Object json = getJSON("/ref/concepts/voidableConcept.json");
        post(json);

        Concept voidableConcept = conceptRepository.findByName("Voidable concept");
        assertThat(voidableConcept).isNotNull();
        assertThat(voidableConcept.isVoided()).isFalse();

        json = getJSON("/ref/concepts/voidedConcept.json");
        post(json);
        Concept voidedConcept = conceptRepository.findByName("Voidable concept");
        assertThat(voidedConcept).isNotNull();
        assertThat(voidedConcept.isVoided()).isTrue();
    }

    @Test
    public void donotChangeTheDataTypeOfConceptUsedAsAnswerIfAlreadyPresent() throws IOException {
        Object json = getJSON("/ref/concepts/conceptUsedAsCodedButAlsoAsAnswer.json");
        post(json);
        assertThat(conceptRepository.findByUuid("d78edcbb-2034-4220-ace2-20b445a1e0ad").getDataType()).isEqualTo(ConceptDataType.Coded.toString());
        assertThat(conceptRepository.findByUuid("60f284a6-0240-4de8-a6a1-8839bc9cc219").getDataType()).isEqualTo(ConceptDataType.Numeric.toString());
        post(json);
        assertThat(conceptRepository.findByUuid("d78edcbb-2034-4220-ace2-20b445a1e0ad").getDataType()).isEqualTo(ConceptDataType.Coded.toString());
        assertThat(conceptRepository.findByUuid("60f284a6-0240-4de8-a6a1-8839bc9cc219").getDataType()).isEqualTo(ConceptDataType.Numeric.toString());
    }

    private Object getJSON(String jsonFile) throws IOException {
        return mapper.readValue(this.getClass().getResource(jsonFile), Object.class);
    }
}

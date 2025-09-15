package org.avni.server.web;

import com.fasterxml.jackson.core.type.TypeReference;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.Concept;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.service.ConceptService;
import org.avni.server.web.request.ConceptContract;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.jdbc.Sql;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ConceptControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private ConceptRepository conceptRepository;
    @Autowired
    private ConceptService conceptService;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void shouldCreateConcepts() throws IOException {
        List<ConceptContract> conceptRequests = readJSON("/ref/concepts/concepts.json");
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);

        Concept naConcept = conceptRepository.findByUuid("b82d4ed8-6e9f-4c67-bfdc-b1a04861bc20");
        assertThat(naConcept).isNotNull();
        assertThat(naConcept.getName()).isEqualTo("NA concept");
    }

    @Test
    public void shouldCreateConceptsWithOneLevelNesting() throws IOException {
        List<ConceptContract> conceptRequests = readJSON("/ref/concepts/codedConceptsWithOneLevelNesting.json");
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);

        Concept nestedConcept = conceptRepository.findByUuid("0ca1c6a2-001b-475a-9813-1d905df9e81b");
        assertThat(nestedConcept).isNotNull();
        assertThat(nestedConcept.getName()).isEqualTo("High Risk Conditions");
    }

    @Test
    public void shouldVoidAConcept() throws IOException {
        List<ConceptContract> conceptRequests = readJSON("/ref/concepts/voidableConcept.json");
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);

        Concept voidableConcept = conceptRepository.findByName("Voidable concept");
        assertThat(voidableConcept).isNotNull();
        assertThat(voidableConcept.isVoided()).isFalse();

        conceptRequests = readJSON("/ref/concepts/voidedConcept.json");
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);
        Concept voidedConcept = conceptRepository.findByName("Voidable concept");
        assertThat(voidedConcept).isNotNull();
        assertThat(voidedConcept.isVoided()).isTrue();
    }

    @Test
    public void doNotChangeTheDataTypeOfConceptUsedAsAnswerIfAlreadyPresent() throws IOException {
        List<ConceptContract> conceptRequests =  readJSON("/ref/concepts/conceptUsedAsCodedButAlsoAsAnswer.json");
        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);
        assertThat(conceptRepository.findByUuid("d78edcbb-2034-4220-ace2-20b445a1e0ad").getDataType()).isEqualTo(ConceptDataType.Coded.toString());
        assertThat(conceptRepository.findByUuid("60f284a6-0240-4de8-a6a1-8839bc9cc219").getDataType()).isEqualTo(ConceptDataType.Numeric.toString());

        conceptService.saveOrUpdateConcepts(conceptRequests, ConceptContract.RequestType.Bundle);
        assertThat(conceptRepository.findByUuid("d78edcbb-2034-4220-ace2-20b445a1e0ad").getDataType()).isEqualTo(ConceptDataType.Coded.toString());
        assertThat(conceptRepository.findByUuid("60f284a6-0240-4de8-a6a1-8839bc9cc219").getDataType()).isEqualTo(ConceptDataType.Numeric.toString());
    }

    private List<ConceptContract> readJSON(String jsonFile) throws IOException {
        return mapper.readValue(this.getClass().getResource(jsonFile), new TypeReference<>() {
        });
    }

}

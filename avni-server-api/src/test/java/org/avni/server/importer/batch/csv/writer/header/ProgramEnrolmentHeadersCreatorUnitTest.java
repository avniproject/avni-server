package org.avni.server.importer.batch.csv.writer.header;

import org.avni.server.application.*;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.application.FormMappingRepository;
import org.avni.server.domain.*;
import org.avni.server.domain.factory.TestAccountBuilder;
import org.avni.server.domain.factory.TestOrganisationBuilder;
import org.avni.server.domain.factory.UserContextBuilder;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.ImportHelperService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.hasItemInArray;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProgramEnrolmentHeadersCreatorUnitTest extends AbstractControllerIntegrationTest {

    @Mock
    private ImportHelperService importHelperService;

    @Mock
    private FormMappingRepository formMappingRepository;

    @Mock
    private FormMapping formMapping;

    @Mock
    private Form form;

    @Mock
    private Program program;

    private ProgramEnrolmentHeadersCreator headersCreator;

    @Before
    public void setUp() {

        MockitoAnnotations.openMocks(this);

        Account account = new TestAccountBuilder().withRegion("IN").build();
        Organisation organisation = new TestOrganisationBuilder().withAccount(account).build();
        UserContext userContext = new UserContextBuilder().withOrganisation(organisation).build();
        UserContextHolder.create(userContext);

        List<FieldDescriptorStrategy> strategyList = Arrays.asList(
                new CodedFieldDescriptor(),
                new DateFieldDescriptor(),
                new TextFieldDescriptor(),
                new NumericFieldDescriptor(),
                new DefaultFieldDescriptor()
        );

        headersCreator = new ProgramEnrolmentHeadersCreator(importHelperService);

        when(formMapping.getForm()).thenReturn(form);
        when(formMapping.getProgram()).thenReturn(program);
        when(program.getName()).thenReturn("TestProgram");
    }

    @Test
    public void testBasicHeaderGeneration() {
        when(form.getApplicableFormElements()).thenReturn(Collections.emptyList());
        when(form.getUuid()).thenReturn(UUID.randomUUID().toString());
        when(formMappingRepository.getProgramsMappedToAForm(anyString())).thenReturn(List.of("TestProgram"));
        String[] headers = headersCreator.getAllHeaders(formMapping);
        String[] description = headersCreator.getAllDescriptions(formMapping);

        assertNotNull(headers);
        Arrays.sort(headers);
        Arrays.sort(description);
        assertEquals("Enrolment Date,Enrolment Location,Exit Date,Exit Location,Id from previous system,Program,Subject Id from previous system", String.join(",", headers));
        assertEquals("\"| Mandatory | Subject id used in subject upload or UUID of subject (can be identified from address bar in Data Entry App or Longitudinal export file) |\",\"| Optional | Can be used to later identify the entry |\",\"| Optional | Format: DD-MM-YYYY or YYYY-MM-DD |\",\"| Optional | Optional | Format: (21.5135243,85.6731848) |\",\"| Optional | Optional | Format: (21.5135243,85.6731848) |\",\"| Optional | Optional | Format: DD-MM-YYYY or YYYY-MM-DD |\",\"| TestProgram |\"", String.join(",", description));
    }

    @Test
    public void testMultipleProgramsForForm() {
        when(form.getApplicableFormElements()).thenReturn(Collections.emptyList());
        when(form.getUuid()).thenReturn(UUID.randomUUID().toString());
        when(formMappingRepository.getProgramsMappedToAForm(anyString()))
                .thenReturn(Arrays.asList("TestProgram", "AnotherProgram"));

        String[] headers = headersCreator.getAllHeaders(formMapping);

        assertThat(headers, hasItemInArray(ProgramEnrolmentHeadersCreator.programHeader));
    }

    @Test
    public void testHeadersWithConceptFields() {
        Concept textConcept = mock(Concept.class);
        when(textConcept.getDataType()).thenReturn(ConceptDataType.Text.name());
        when(textConcept.getName()).thenReturn("TextConcept");
        FormElement textFormElement = mock(FormElement.class);
        when(textFormElement.getConcept()).thenReturn(textConcept);
        when(textFormElement.isMandatory()).thenReturn(false);
        when(textFormElement.getGroup()).thenReturn(null);
        when(importHelperService.getHeaderName(textFormElement)).thenReturn("\"TextConcept\"");

        Concept numericConcept = mock(Concept.class);
        when(numericConcept.getDataType()).thenReturn(ConceptDataType.Numeric.name());
        when(numericConcept.getName()).thenReturn("NumericConcept");
        when(numericConcept.getHighAbsolute()).thenReturn(5.0);
        when(numericConcept.getLowAbsolute()).thenReturn(2.0);
        FormElement numericFormElement = mock(FormElement.class);
        when(numericFormElement.getConcept()).thenReturn(numericConcept);
        when(numericFormElement.isMandatory()).thenReturn(false);
        when(numericFormElement.getGroup()).thenReturn(null);
        when(importHelperService.getHeaderName(numericFormElement)).thenReturn("\"NumericConcept\"");

        Concept dateConcept = mock(Concept.class);
        when(dateConcept.getDataType()).thenReturn(ConceptDataType.Date.name());
        when(dateConcept.getName()).thenReturn("DateConcept");
        FormElement dateFormElement = mock(FormElement.class);
        when(dateFormElement.getConcept()).thenReturn(dateConcept);
        when(dateFormElement.isMandatory()).thenReturn(false);
        when(dateFormElement.getGroup()).thenReturn(null);
        when(importHelperService.getHeaderName(dateFormElement)).thenReturn("\"DateConcept\"");

        Concept codedConcept = mock(Concept.class);
        when(codedConcept.getDataType()).thenReturn(ConceptDataType.Coded.name());
        when(codedConcept.getName()).thenReturn("CodedConcept");

        FormElement codedFormElement = mock(FormElement.class);
        when(codedFormElement.getConcept()).thenReturn(codedConcept);
        when(codedFormElement.isMandatory()).thenReturn(false);
        when(codedFormElement.getType()).thenReturn(FormElementType.MultiSelect.toString());
        when(codedFormElement.getGroup()).thenReturn(null);
        when(importHelperService.getHeaderName(codedFormElement)).thenReturn("\"CodedConcept\"");

        Concept ans1 = mock(Concept.class);
        when(ans1.getName()).thenReturn("Ans1");
        when(ans1.getDataType()).thenReturn(ConceptDataType.Text.name());

        Concept ans2 = mock(Concept.class);
        when(ans2.getName()).thenReturn("Ans2");
        when(ans2.getDataType()).thenReturn(ConceptDataType.Text.name());

        ConceptAnswer ca1 = mock(ConceptAnswer.class);
        when(ca1.getAnswerConcept()).thenReturn(ans1);
        when(ca1.getConcept()).thenReturn(codedConcept);

        ConceptAnswer ca2 = mock(ConceptAnswer.class);
        when(ca2.getAnswerConcept()).thenReturn(ans2);
        when(ca2.getConcept()).thenReturn(codedConcept);

        Set<ConceptAnswer> conceptAnswers = new HashSet<>(Arrays.asList(ca1, ca2));
        when(codedConcept.getConceptAnswers()).thenReturn(conceptAnswers);

        Concept readOnlyConcept = mock(Concept.class);
        when(readOnlyConcept.getDataType()).thenReturn(ConceptDataType.Text.name());
        when(readOnlyConcept.getName()).thenReturn("ReadOnlyConcept");
        FormElement readOnlyFormElement = mock(FormElement.class);
        when(readOnlyFormElement.getConcept()).thenReturn(readOnlyConcept);
        when(readOnlyFormElement.isMandatory()).thenReturn(false);
        when(readOnlyFormElement.getGroup()).thenReturn(null);


        KeyValues keyValues = new KeyValues();
        keyValues.add(new KeyValue(KeyType.editable, false));
        when(readOnlyFormElement.getKeyValues()).thenReturn(keyValues);
        when(importHelperService.getHeaderName(readOnlyFormElement)).thenReturn("\"ReadOnlyConcept\"");

        when(form.getApplicableFormElements()).thenReturn(Arrays.asList(
                textFormElement, numericFormElement, dateFormElement, codedFormElement  ,readOnlyFormElement
        ));
        when(form.getUuid()).thenReturn(UUID.randomUUID().toString());
        when(formMappingRepository.getProgramsMappedToAForm(anyString())).thenReturn(Collections.singletonList("TestProgram"));

        String[] headers = headersCreator.getAllHeaders(formMapping);
        String[] descriptions = headersCreator.getAllDescriptions(formMapping);

        assertThat(headers, hasItemInArray("\"TextConcept\""));
        assertThat(headers, hasItemInArray("\"NumericConcept\""));
        assertThat(headers, hasItemInArray("\"DateConcept\""));
        assertThat(headers, hasItemInArray("\"CodedConcept\""));
        assertThat(headers, hasItemInArray("\"ReadOnlyConcept\""));

        assertThat(descriptions, hasItemInArray("\"| Optional | Any Text |\""));
        assertThat(descriptions, hasItemInArray("\"| Optional | Min value allowed: 2.0 Max value allowed: 5.0 |\""));
        assertThat(descriptions, hasItemInArray("\"| Optional | Format: DD-MM-YYYY |\""));
        assertThat(Arrays.asList(descriptions), hasItem(anyOf(
                containsString("\"| Optional | Allowed values: {Ans1, Ans2} Format: Separate multiple values by a comma. |\""),
                containsString("\"| Optional | Allowed values: {Ans2, Ans1} Format: Separate multiple values by a comma. |\"")
        )));
        assertThat(descriptions, hasItemInArray("\"| Optional | Any Text | The value can be auto-calculated if not entered |\""));
    }

    @Test
    public void testDescriptionsGeneration() {
        String[] descriptions = headersCreator.getAllDescriptions(formMapping);

        assertNotNull(descriptions);
        assertEquals(headersCreator.getAllHeaders(formMapping).length, descriptions.length,
                "Descriptions array should match headers array length");
    }

}

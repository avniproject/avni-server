package org.avni.server.exporter;

import org.avni.server.dao.EncounterRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.AddressLevel;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.Encounter;
import org.avni.server.domain.EncounterType;
import org.avni.server.domain.Individual;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class ExportReferenceResolverTest {
    private static final String SUBJECT = ConceptDataType.Subject.toString();
    private static final String LOCATION = ConceptDataType.Location.toString();
    private static final String ENCOUNTER = ConceptDataType.Encounter.toString();

    @Mock
    private IndividualRepository individualRepository;
    @Mock
    private LocationRepository locationRepository;
    @Mock
    private EncounterRepository encounterRepository;

    private ExportReferenceResolver resolver;

    @Before
    public void setup() {
        initMocks(this);
        resolver = new ExportReferenceResolver(individualRepository, locationRepository, encounterRepository);
    }

    @Test
    public void referenceTypesAreTheThreeThatStoreAUuid() {
        assertTrue(ExportReferenceResolver.isReferenceType(SUBJECT));
        assertTrue(ExportReferenceResolver.isReferenceType(LOCATION));
        assertTrue(ExportReferenceResolver.isReferenceType(ENCOUNTER));

        assertFalse(ExportReferenceResolver.isReferenceType(ConceptDataType.Text.toString()));
        assertFalse(ExportReferenceResolver.isReferenceType(ConceptDataType.Coded.toString()));
        assertFalse(ExportReferenceResolver.isReferenceType(ConceptDataType.Image.toString()));
    }

    @Test
    public void aSubjectAnswerCarriesBothTheNameAndTheIdentifier() {
        when(individualRepository.findAllByUuidIn(Collections.singletonList("s1")))
                .thenReturn(Collections.singletonList(subject("s1", "GHS Wadagera")));

        assertEquals("GHS Wadagera(s1)", resolver.resolve(SUBJECT, "s1"));
    }

    @Test
    public void aMultiSelectSubjectAnswerResolvesToEveryNameAndNeverToAListLiteral() {
        when(individualRepository.findAllByUuidIn(Arrays.asList("s1", "s2")))
                .thenReturn(Arrays.asList(subject("s1", "GHS Wadagera"), subject("s2", "GHS Manvi")));

        String resolved = resolver.resolve(SUBJECT, Arrays.asList("s1", "s2"));

        assertEquals("GHS Wadagera(s1); GHS Manvi(s2)", resolved);
        assertFalse(resolved.startsWith("["));
    }

    @Test
    public void aNameIsFetchedOnceHoweverManyRowsRepeatTheReference() {
        when(individualRepository.findAllByUuidIn(Collections.singletonList("s1")))
                .thenReturn(Collections.singletonList(subject("s1", "GHS Wadagera")));

        assertEquals("GHS Wadagera(s1)", resolver.resolve(SUBJECT, "s1"));
        assertEquals("GHS Wadagera(s1)", resolver.resolve(SUBJECT, "s1"));
        assertEquals("GHS Wadagera(s1)", resolver.resolve(SUBJECT, "s1"));

        verify(individualRepository, times(1)).findAllByUuidIn(anyList());
    }

    @Test
    public void aRowsUnseenReferencesAreFetchedInOneQuery() {
        when(individualRepository.findAllByUuidIn(Arrays.asList("s1", "s2", "s3")))
                .thenReturn(Arrays.asList(subject("s1", "A"), subject("s2", "B"), subject("s3", "C")));

        assertEquals("A(s1); B(s2); C(s3)", resolver.resolve(SUBJECT, Arrays.asList("s1", "s2", "s3")));

        verify(individualRepository, times(1)).findAllByUuidIn(anyList());
    }

    @Test
    public void aReferenceToARecordThisExportCannotSeeKeepsItsIdentifier() {
        when(individualRepository.findAllByUuidIn(anyList())).thenReturn(Collections.emptyList());

        assertEquals("deleted-uuid", resolver.resolve(SUBJECT, "deleted-uuid"));
    }

    @Test
    public void aMissIsNotLookedUpAgain() {
        when(individualRepository.findAllByUuidIn(anyList())).thenReturn(Collections.emptyList());

        resolver.resolve(SUBJECT, "deleted-uuid");
        resolver.resolve(SUBJECT, "deleted-uuid");

        verify(individualRepository, times(1)).findAllByUuidIn(anyList());
    }

    @Test
    public void anAnswerThatWasNeverGivenCostsNoQuery() {
        assertEquals("", resolver.resolve(SUBJECT, null));
        assertEquals("", resolver.resolve(LOCATION, null));
        assertEquals("", resolver.resolve(ENCOUNTER, Collections.emptyList()));

        verify(individualRepository, never()).findAllByUuidIn(anyList());
        verify(locationRepository, never()).findByUuidIn(anyList());
        verify(encounterRepository, never()).findAllByUuidIn(anyList());
    }

    @Test
    public void aLocationAnswerResolvesToTheLocationTitle() {
        AddressLevel addressLevel = new AddressLevel();
        addressLevel.setUuid("l1");
        addressLevel.setTitle("Manvi");
        when(locationRepository.findByUuidIn(Collections.singletonList("l1")))
                .thenReturn(Collections.singletonList(addressLevel));

        assertEquals("Manvi(l1)", resolver.resolve(LOCATION, "l1"));
    }

    @Test
    public void anEncounterAnswerFallsBackToItsTypeWhenTheVisitHasNoNameOfItsOwn() {
        when(encounterRepository.findAllByUuidIn(Arrays.asList("e1", "e2")))
                .thenReturn(Arrays.asList(encounter("e1", "Follow-up 3", "WASH Audit"), encounter("e2", null, "WASH Audit")));

        assertEquals("Follow-up 3(e1); WASH Audit(e2)", resolver.resolve(ENCOUNTER, Arrays.asList("e1", "e2")));
    }

    @Test
    public void twoUnnamedVisitsOfTheSameTypeStayTellableApartByTheirIdentifier() {
        when(encounterRepository.findAllByUuidIn(Arrays.asList("e2", "e3")))
                .thenReturn(Arrays.asList(encounter("e2", null, "WASH Audit"), encounter("e3", null, "WASH Audit")));

        assertEquals("WASH Audit(e2); WASH Audit(e3)", resolver.resolve(ENCOUNTER, Arrays.asList("e2", "e3")));
    }

    @Test
    public void eachTypeIsCachedSeparatelySoTheyCannotAnswerForEachOther() {
        when(individualRepository.findAllByUuidIn(anyList())).thenReturn(Collections.singletonList(subject("x", "A subject")));
        when(locationRepository.findByUuidIn(anyList())).thenReturn(Collections.emptyList());

        assertEquals("A subject(x)", resolver.resolve(SUBJECT, "x"));
        assertEquals("x", resolver.resolve(LOCATION, "x"));
    }

    @Test
    public void pastTheCacheCapItKeepsAnsweringCorrectlyAndStopsRemembering() {
        ExportReferenceResolver capped = new ExportReferenceResolver(individualRepository, locationRepository, encounterRepository, 2);
        for (String uuid : Arrays.asList("s1", "s2", "s3")) {
            when(individualRepository.findAllByUuidIn(Collections.singletonList(uuid)))
                    .thenReturn(Collections.singletonList(subject(uuid, "Name " + uuid)));
        }

        assertEquals("Name s1(s1)", capped.resolve(SUBJECT, "s1"));
        assertEquals("Name s2(s2)", capped.resolve(SUBJECT, "s2"));
        assertEquals("Name s3(s3)", capped.resolve(SUBJECT, "s3"));

        // The two that fitted are still served without a query.
        assertEquals("Name s1(s1)", capped.resolve(SUBJECT, "s1"));
        assertEquals("Name s2(s2)", capped.resolve(SUBJECT, "s2"));
        verify(individualRepository, times(1)).findAllByUuidIn(Collections.singletonList("s1"));
        verify(individualRepository, times(1)).findAllByUuidIn(Collections.singletonList("s2"));

        // The one past the cap is answered from what was just fetched, and re-queried next time
        // rather than copying or scanning the cache.
        assertEquals("Name s3(s3)", capped.resolve(SUBJECT, "s3"));
        verify(individualRepository, times(2)).findAllByUuidIn(Collections.singletonList("s3"));
    }

    private Individual subject(String uuid, String firstName) {
        Individual individual = new Individual();
        individual.setUuid(uuid);
        individual.setFirstName(firstName);
        return individual;
    }

    private Encounter encounter(String uuid, String name, String encounterTypeName) {
        Encounter encounter = new Encounter();
        encounter.setUuid(uuid);
        encounter.setName(name);
        EncounterType encounterType = new EncounterType();
        encounterType.setName(encounterTypeName);
        encounter.setEncounterType(encounterType);
        return encounter;
    }
}

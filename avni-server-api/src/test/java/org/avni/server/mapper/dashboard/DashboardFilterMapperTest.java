package org.avni.server.mapper.dashboard;

import org.avni.server.dao.ConceptRepository;
import org.avni.server.domain.JsonObject;
import org.avni.server.domain.app.dashboard.DashboardFilter;
import org.avni.server.domain.app.dashboard.DashboardFilter.FilterType;
import org.avni.server.web.contract.DashboardFilterConfigContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A Subject Type filter can carry a preset subject type and an optional preset program (avniproject/avni-server#1102).
 * The web app reads them from the dashboard it opens and a bundle carries them to another organisation, so both come
 * out of the stored filter under the names written out here, which are the names in the stored JSON. Only a Subject
 * Type filter has them.
 */
public class DashboardFilterMapperTest {
    private static final String PRESET_SUBJECT_TYPE_KEY = "presetSubjectTypeUUID";
    private static final String PRESET_PROGRAM_KEY = "presetProgramUUID";

    private DashboardFilterMapper mapper;

    @Before
    public void setUp() {
        mapper = new DashboardFilterMapper(Mockito.mock(ConceptRepository.class));
    }

    @Test
    public void responseCarriesThePresetOfASubjectTypeFilter() {
        DashboardFilterConfigContract config = mapper.toResponse(filterStoredAs(subjectTypeConfigWithPreset())).getFilterConfig();

        assertEquals("st-1", config.getPresetSubjectTypeUUID());
        assertEquals("pr-1", config.getPresetProgramUUID());
        assertNull("the filter's own subject type, read back from a filter that has only a preset", config.getSubjectTypeUUID());
    }

    @Test
    public void bundleCarriesThePresetOfASubjectTypeFilter() {
        DashboardFilterConfigContract config = mapper.toBundle(filterStoredAs(subjectTypeConfigWithPreset())).getFilterConfig();

        assertEquals("st-1", config.getPresetSubjectTypeUUID());
        assertEquals("pr-1", config.getPresetProgramUUID());
        assertNull("the filter's own subject type, in a bundle of a filter that has only a preset", config.getSubjectTypeUUID());
    }

    // A filter saved before the preset existed has neither key. It reads back as no preset, not as an error.
    @Test
    public void subjectTypeFilterSavedBeforeThePresetReadsBackWithNone() {
        DashboardFilterConfigContract config = mapper.toResponse(filterStoredAs(new JsonObject().with("type", "SubjectType"))).getFilterConfig();

        // The type is read too: a config that came back empty would also have no preset.
        assertEquals("SubjectType", config.getType());
        assertNull("preset subject type of a filter saved before the preset", config.getPresetSubjectTypeUUID());
        assertNull("preset program of a filter saved before the preset", config.getPresetProgramUUID());
    }

    // Each of these is stored with both preset keys, which the save never gives a filter of this type, so only the mapper
    // can keep them out. They are the types that read back from nothing but their own type. A Group Subject or Concept
    // filter needs its own settings to read back at all, and the mapper deals with those two before it reaches Subject Type.
    @Test
    public void otherFilterTypesReadBackWithNoPreset() {
        FilterType[] otherTypes = {FilterType.AsOnDate, FilterType.RegistrationDate, FilterType.Gender, FilterType.EnrolmentDate,
                FilterType.ProgramEncounterDate, FilterType.EncounterDate, FilterType.Address};
        for (FilterType filterType : otherTypes) {
            JsonObject stored = new JsonObject().with("type", filterType.name())
                    .with(PRESET_SUBJECT_TYPE_KEY, "st-1")
                    .with(PRESET_PROGRAM_KEY, "pr-1");

            DashboardFilterConfigContract config = mapper.toResponse(filterStoredAs(stored)).getFilterConfig();

            assertEquals(filterType.name(), config.getType());
            assertNull(filterType + " filter read back with a preset subject type", config.getPresetSubjectTypeUUID());
            assertNull(filterType + " filter read back with a preset program", config.getPresetProgramUUID());
        }
    }

    private static JsonObject subjectTypeConfigWithPreset() {
        return new JsonObject().with("type", "SubjectType")
                .with(PRESET_SUBJECT_TYPE_KEY, "st-1")
                .with(PRESET_PROGRAM_KEY, "pr-1");
    }

    private static DashboardFilter filterStoredAs(JsonObject storedConfig) {
        DashboardFilter filter = new DashboardFilter();
        filter.setUuid("filter-1");
        filter.setName("Screening type");
        filter.setFilterConfig(storedConfig);
        return filter;
    }
}

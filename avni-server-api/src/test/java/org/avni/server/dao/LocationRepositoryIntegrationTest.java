package org.avni.server.dao;

import org.springframework.transaction.annotation.Transactional;
import org.avni.server.application.projections.LocationProjection;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.domain.AddressLevel;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Sql({"/test-data.sql"})
@Transactional
public class LocationRepositoryIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private LocationRepository locationRepository;

    @Test
    public void testFindByTitleAndAddressLevelType() {
        AddressLevel addressLevel = locationRepository.findById(1L).get();
        List<AddressLevel> addressLevels = locationRepository.findByTitleAndType(addressLevel.getTitle(), addressLevel.getType(), PageRequest.of(0, 1));
        assertThat(addressLevels.size()).isEqualTo(1);

        addressLevels = locationRepository.findByTitleAndType("non-existent address", addressLevel.getType(), PageRequest.of(0, 1));
        assertThat(addressLevels.size()).isEqualTo(0);
    }

    @Test
    public void findByAncestorAndFiltersShouldReturnAncestorAndItsDescendants() {
        // test-data.sql sets up GP1 (id=3, lineage='3') with descendants GP1.Parent1 (id=4, '3.4') and GP1.Parent1.Child1 (id=5, '3.4.5')
        // The selected ancestor itself is included alongside its descendants so drilling to any location surfaces that location.
        Page<LocationProjection> result = locationRepository.findLocationProjectionByAncestorAndFilters(
                null, null, "3", PageRequest.of(0, 50));

        assertThat(result.getContent()).extracting(LocationProjection::getId).containsExactlyInAnyOrder(3L, 4L, 5L);
    }

    @Test
    public void findByAncestorAndFiltersShouldReturnLeafItselfWhenAncestorIsALeaf() {
        // Drilling the cascade all the way to a leaf (GP1.Parent1.Child1, id=5, lineage='3.4.5') must return that leaf,
        // even though it has no descendants of its own.
        Page<LocationProjection> result = locationRepository.findLocationProjectionByAncestorAndFilters(
                null, null, "3.4.5", PageRequest.of(0, 50));

        assertThat(result.getContent()).extracting(LocationProjection::getId).containsExactly(5L);
    }

    @Test
    public void findByAncestorAndFiltersShouldNotMatchSiblingsWithSimilarLineagePrefix() {
        // GP2 (id=6, lineage='6') and descendants must not appear when ancestor is GP1 (lineage='3').
        // Integer-id dot separator prevents false prefix matches (e.g. '3' should not match '30').
        Page<LocationProjection> result = locationRepository.findLocationProjectionByAncestorAndFilters(
                null, null, "3", PageRequest.of(0, 50));

        assertThat(result.getContent()).extracting(LocationProjection::getId).doesNotContain(6L, 7L, 8L);
    }

    @Test
    public void findByAncestorAndFiltersShouldReturnAllNonVoidedWhenAllFiltersAreNull() {
        Page<LocationProjection> result = locationRepository.findLocationProjectionByAncestorAndFilters(
                null, null, null, PageRequest.of(0, 50));

        // All eight seeded address_levels are non-voided.
        assertThat(result.getContent()).hasSizeGreaterThanOrEqualTo(8);
    }
}

package org.avni.server.service;

import org.avni.server.application.projections.VirtualCatchmentProjection;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.LocationRepository;
import org.avni.server.domain.Catchment;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

import static java.util.Arrays.asList;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.mockito.Mockito.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AddressLevelCacheIntegrationTest extends AbstractControllerIntegrationTest {

    @Mock
    private LocationRepository mockLocationRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AddressLevelCache addressLevelCache;

    private Catchment catchmentKey1;

    @Before
    public void setUpAddressLevelCache() {
        //Init Catchment
        catchmentKey1 = new Catchment();
        catchmentKey1.setId(1L);
        List<VirtualCatchmentProjection> catchmentKey1ResponseList = new ArrayList<>(asList(new VirtualCatchmentProjectTestImplementation(1L, 1L, 1L, 1L), new VirtualCatchmentProjectTestImplementation(2L, 2L, 1L, 1L)));

        //Reset mocks
        reset(mockLocationRepository);

        //Use mock inside AddressLevelCache
        ReflectionTestUtils.setField(addressLevelCache, "locationRepository", mockLocationRepository);

        //stubbing
        when(mockLocationRepository.getVirtualCatchmentsForCatchmentId(catchmentKey1.getId())).thenReturn(catchmentKey1ResponseList);
    }


    @Test
    public void givenAddressLevelCacheIsConfigured_whenCallGetVirtualCatchmentsForCatchmentId_thenDataShouldBeInAddressPerCatchmentCache() {
        //Fetch and cache
        List<VirtualCatchmentProjection> cachedVirtualCatchmentProjList = addressLevelCache.getAddressLevelsForCatchment(catchmentKey1);

        //Validate cache content
        Assert.notNull(cachedVirtualCatchmentProjList, "addrPerCatchmentCache should have had the data");
        Assert.isTrue(2 == cachedVirtualCatchmentProjList.size(), "addrPerCatchmentCache size should have been 2");
    }

    @Test
    public void givenAddressLevelCacheIsConfigured_whenMultipleCallGetVirtualCatchmentsForCatchmentId_thenValidateCacheMissAndHits() {
        //Fetch and cache
        addressLevelCache.getAddressLevelsForCatchment(catchmentKey1);

        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);
        Assert.notNull(addrPerCatchmentCache.get(catchmentKey1).get(), "addrPerCatchmentCache should have had the data");

        //Validate cache miss the first time
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchmentKey1.getId());

        //Invoke cache multiple times
        addrPerCatchmentCache.get(catchmentKey1).get();
        addrPerCatchmentCache.get(catchmentKey1).get();
        addrPerCatchmentCache.get(catchmentKey1).get();

        //Validate cache hits
        verifyNoMoreInteractions(mockLocationRepository);
    }
}
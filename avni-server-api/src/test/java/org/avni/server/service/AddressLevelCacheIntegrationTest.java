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

import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.mockito.Mockito.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AddressLevelCacheIntegrationTest extends AbstractControllerIntegrationTest {

    public static final long ADDRESS_LEVEL_TYPE_ID = 1L;
    public static final long CATCHMENT_1_ID = 1L;
    public static final long CATCHMENT_2_ID = 2L;

    @Mock
    private LocationRepository mockLocationRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AddressLevelCache addressLevelCache;

    private Catchment catchment1, catchment2;

    @Before
    public void setUpAddressLevelCache() {
        //Reset mocks
        reset(mockLocationRepository);

        //Use mock inside AddressLevelCache
        ReflectionTestUtils.setField(addressLevelCache, "locationRepository", mockLocationRepository);

        catchment1 = initCatchmentAndMock(CATCHMENT_1_ID, 1L, 2);
        catchment2 = initCatchmentAndMock(CATCHMENT_2_ID, 3L, 10);
    }

    private Catchment initCatchmentAndMock(long catchment1Id, long startIndex, int numberOfEntries) {
        //Init Catchment
        Catchment catchment = new Catchment();
        catchment.setId(catchment1Id);
        List<VirtualCatchmentProjection> catchmentResponseList = getVirtualCatchmentProjectionArrayList(catchment1Id, startIndex, numberOfEntries);

        //stubbing
        when(mockLocationRepository.getVirtualCatchmentsForCatchmentId(catchment.getId())).thenReturn(catchmentResponseList);

        return catchment;
    }


    @Test
    public void givenAddressLevelCacheIsConfigured_whenCallGetVirtualCatchmentsForCatchmentId_thenDataShouldBeInAddressPerCatchmentCache() {
        //Fetch and cache
        List<VirtualCatchmentProjection> cachedVirtualCatchmentProjList = addressLevelCache.getAddressLevelsForCatchment(catchment1);

        //Validate cache content
        Assert.notNull(cachedVirtualCatchmentProjList, "addrPerCatchmentCache should have had the data");
        Assert.isTrue(2 == cachedVirtualCatchmentProjList.size(), "addrPerCatchmentCache size should have been 2");
    }

    @Test
    public void givenAddressLevelCacheIsConfigured_whenMultipleCallGetVirtualCatchmentsForCatchmentId_thenValidateCacheMissAndHits() {
        //Fetch and cache
        addressLevelCache.getAddressLevelsForCatchment(catchment1);

        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);
        Assert.notNull(addrPerCatchmentCache.get(catchment1).get(), "addrPerCatchmentCache should have had the data");
        Assert.isTrue(2 == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment1).get()).size(),
                "addrPerCatchmentCache size should have been 2");

        //Validate cache miss the first time
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment1.getId());

        //Invoke cache multiple times
        addressLevelCache.getAddressLevelsForCatchment(catchment1);
        addressLevelCache.getAddressLevelsForCatchment(catchment1);
        addressLevelCache.getAddressLevelsForCatchment(catchment1);

        //Validate cache hits
        verifyNoMoreInteractions(mockLocationRepository);
    }

    @Test
    public void givenAddressLevelCacheIsConfigured_whenCallGetVirtualCatchmentsForDiffCatchmentId_thenValidateCacheMiss() {
        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);
        Assert.isNull(addrPerCatchmentCache.get(catchment2), "addrPerCatchmentCache2 should not have had the data");

        addressLevelCache.getAddressLevelsForCatchment(catchment2);
        Assert.isTrue(10 == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment2).get()).size(),
                "addrPerCatchmentCache2 size should have been 10");

        //Validate cache miss the first time
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment1.getId());
    }

    private ArrayList<VirtualCatchmentProjection> getVirtualCatchmentProjectionArrayList(Long catchmentId, long startIndex, int numberOfEntries) {
        ArrayList<VirtualCatchmentProjection> virtualCatchmentProjectionList = new ArrayList<>(numberOfEntries);
        for (long i = startIndex; i < startIndex + numberOfEntries; i++) {
            virtualCatchmentProjectionList.add(new VirtualCatchmentProjectTestImplementation(i, i, catchmentId, ADDRESS_LEVEL_TYPE_ID));
        }
        return virtualCatchmentProjectionList;
    }
}
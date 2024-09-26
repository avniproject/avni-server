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

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.mockito.Mockito.*;

@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class AddressLevelCacheIntegrationTest extends AbstractControllerIntegrationTest {

    private static final long ADDRESS_LEVEL_TYPE_ID = 1L;
    private static final long CATCHMENT_1_ID = 1L;
    private static final long CATCHMENT_2_ID = 2L;
    private static final long CATCHMENT_3_ID = 3L;
    private static final long CATCHMENT_4_ID = 4L;
    private static final int CATCHMENT_1_SIZE = 2;
    private static final int CATCHMENT_2_SIZE = 10;
    private static final int CATCHMENT_3_SIZE = 8;
    private static final int CATCHMENT_4_SIZE = 20;

    private long addressIdStartIdx;

    @Mock
    private LocationRepository mockLocationRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private AddressLevelCache addressLevelCache;

    private Catchment catchment1, catchment2, catchment3, catchment4;

    @Before
    public void setUpAddressLevelCache() {
        addressIdStartIdx = 1L;
        //Reset mocks
        reset(mockLocationRepository);

        //Use mock inside AddressLevelCache
        ReflectionTestUtils.setField(addressLevelCache, "locationRepository", mockLocationRepository);

        catchment1 = initCatchmentAndMock(CATCHMENT_1_ID, addressIdStartIdx, CATCHMENT_1_SIZE);
        catchment2 = initCatchmentAndMock(CATCHMENT_2_ID, getAddressIdStartIdx( CATCHMENT_1_SIZE), CATCHMENT_2_SIZE);
        catchment3 = initCatchmentAndMock(CATCHMENT_3_ID, getAddressIdStartIdx( CATCHMENT_2_SIZE), CATCHMENT_3_SIZE);
        catchment4 = initCatchmentAndMock(CATCHMENT_4_ID, getAddressIdStartIdx( CATCHMENT_3_SIZE), CATCHMENT_4_SIZE);
    }

    private long getAddressIdStartIdx(long offset) {
        return addressIdStartIdx += offset;
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

    private ArrayList<VirtualCatchmentProjection> getVirtualCatchmentProjectionArrayList(Long catchmentId, long startIndex, int numberOfEntries) {
        ArrayList<VirtualCatchmentProjection> virtualCatchmentProjectionList = new ArrayList<>(numberOfEntries);
        for (long i = startIndex; i < startIndex + numberOfEntries; i++) {
            virtualCatchmentProjectionList.add(new VirtualCatchmentProjectTestImplementation(i, i, catchmentId, ADDRESS_LEVEL_TYPE_ID));
        }
        return virtualCatchmentProjectionList;
    }

    @Test
    public void givenAddressLevelCacheIsConfigured_whenCallGetVirtualCatchmentsForCatchmentId_thenDataShouldBeInAddressPerCatchmentCache() {
        //Fetch and cache
        List<VirtualCatchmentProjection> cachedVirtualCatchmentProjList = addressLevelCache.getAddressLevelsForCatchment(catchment1);

        //Validate cache content
        Assert.notNull(cachedVirtualCatchmentProjList, "addrPerCatchmentCache should have had the data");
        Assert.isTrue(CATCHMENT_1_SIZE == cachedVirtualCatchmentProjList.size(), "addrPerCatchmentCache size should have been 2");
    }

    @Test
    public void givenAddressLevelCacheIsConfigured_whenMultipleCallGetVirtualCatchmentsForCatchmentId_thenValidateCacheMissAndHits() {
        //Fetch and cache
        addressLevelCache.getAddressLevelsForCatchment(catchment1);

        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);
        Assert.notNull(addrPerCatchmentCache.get(catchment1).get(), "addrPerCatchmentCache should have had the data");
        Assert.isTrue(CATCHMENT_1_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment1).get()).size(),
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
        Assert.isTrue(CATCHMENT_2_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment2).get()).size(),
                "addrPerCatchmentCache2 size should have been 10");

        //Validate cache miss the first time
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment2.getId());
    }

    @Test
    public void givenAddressLevelCachesAreFullyPopulated_whenCallGetVirtualCatchmentsForNewCatchmentId_thenValidateCacheClearedAndNewOneIsPopulated() {
        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);
        Assert.isNull(addrPerCatchmentCache.get(catchment1), "addrPerCatchmentCache1 should not have had the data");
        Assert.isNull(addrPerCatchmentCache.get(catchment2), "addrPerCatchmentCache2 should not have had the data");
        Assert.isNull(addrPerCatchmentCache.get(catchment3), "addrPerCatchmentCache3 should not have had the data");
        Assert.isNull(addrPerCatchmentCache.get(catchment4), "addrPerCatchmentCache4 should not have had the data");

        //Invoke for different catchments
        addressLevelCache.getAddressLevelsForCatchment(catchment1);
        addressLevelCache.getAddressLevelsForCatchment(catchment2);
        addressLevelCache.getAddressLevelsForCatchment(catchment3);

        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment1.getId());
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment2.getId());
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment3.getId());

        verifyNoMoreInteractions(mockLocationRepository);

        //Ensure cache has all the data
        Assert.notNull(addrPerCatchmentCache.get(catchment1).get(), "addrPerCatchmentCache1 should have had the data");
        Assert.isTrue(CATCHMENT_1_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment1).get()).size(),
                "addrPerCatchmentCache1 size should have been 2");
        Assert.notNull(addrPerCatchmentCache.get(catchment2).get(), "addrPerCatchmentCache2 should have had the data");
        Assert.isTrue(CATCHMENT_2_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment2).get()).size(),
                "addrPerCatchmentCache2 size should have been 10");
        Assert.notNull(addrPerCatchmentCache.get(catchment3).get(), "addrPerCatchmentCache3 should have had the data");
        Assert.isTrue(CATCHMENT_3_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment3).get()).size(),
                "addrPerCatchmentCache3 size should have been 8");

        //Invoke for a new catchment
        addressLevelCache.getAddressLevelsForCatchment(catchment4);

        //Validate cache miss the first time for new catchment
        verify(mockLocationRepository).getVirtualCatchmentsForCatchmentId(catchment4.getId());
        verifyNoMoreInteractions(mockLocationRepository);

        //Ensure cache has overflown and first entry got replaced with new catchment entry
        Assert.isTrue(Objects.isNull(addrPerCatchmentCache.get(catchment1))
                        || Objects.isNull(addrPerCatchmentCache.get(catchment2))
                        || Objects.isNull(addrPerCatchmentCache.get(catchment3)),
                "addrPerCatchmentCache 1,2 or 3 should not have had the data");
        Assert.isTrue(CATCHMENT_4_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment4).get()).size(),
                "addrPerCatchmentCache4 size should have been 20");
    }


    @Test
    public void givenAddressLevelCachesAreFullyPopulated_whenCallGetVirtualCatchmentsForNewCatchmentId_thenValidateCacheClearedAndGCRemovesTheOldEntryFromMemory() {
        Cache addrPerCatchmentCache = cacheManager.getCache(ADDRESSES_PER_CATCHMENT);

        //Invoke for different catchments
        addressLevelCache.getAddressLevelsForCatchment(catchment1);
        addressLevelCache.getAddressLevelsForCatchment(catchment2);
        addressLevelCache.getAddressLevelsForCatchment(catchment3);

        //Make weakReferences to later check the ReferenceQueue for System GC removing them from memory
        ReferenceQueue<Object> keysReferenceQueue = new ReferenceQueue<>();
        ReferenceQueue<Object> valuesReferenceQueue = new ReferenceQueue<>();

        WeakReference weakReferenceForCatchment1 = new WeakReference<>(addrPerCatchmentCache.get(catchment1), keysReferenceQueue);
        WeakReference weakReferenceForCatchment2 = new WeakReference<>(addrPerCatchmentCache.get(catchment2), keysReferenceQueue);
        WeakReference weakReferenceForCatchment3 = new WeakReference<>(addrPerCatchmentCache.get(catchment3), keysReferenceQueue);
        WeakReference weakReferenceForCatchment1Value = new WeakReference<>(addrPerCatchmentCache.get(catchment1).get(), valuesReferenceQueue);
        WeakReference weakReferenceForCatchment2Value = new WeakReference<>(addrPerCatchmentCache.get(catchment2).get(), valuesReferenceQueue);
        WeakReference weakReferenceForCatchment3Value = new WeakReference<>(addrPerCatchmentCache.get(catchment3).get(), valuesReferenceQueue);

        //Invoke for a new catchment
        addressLevelCache.getAddressLevelsForCatchment(catchment4);
        //Ensure cache has overflown and first entry got replaced with new catchment entry
        Assert.isTrue(Objects.isNull(addrPerCatchmentCache.get(catchment1))
                        | Objects.isNull(addrPerCatchmentCache.get(catchment2))
                        | Objects.isNull(addrPerCatchmentCache.get(catchment3)),
                "addrPerCatchmentCache 1,2 or 3 should not have had the data");
        Assert.isTrue(CATCHMENT_4_SIZE == ((List<VirtualCatchmentProjection>) addrPerCatchmentCache.get(catchment4).get()).size(),
                "addrPerCatchmentCache4 size should have been 20");

        // Remove references to catchment Response lists
        reset(mockLocationRepository);

        //Explicitly invoke Garbage collection, so that old catchment1 with only weak references is cleared from memory
        System.gc();

        //Validate that catchment1 entries are no longer retained in memory
        Assert.isTrue(Objects.isNull(weakReferenceForCatchment1.get())
                | Objects.isNull(weakReferenceForCatchment2.get())
                | Objects.isNull(weakReferenceForCatchment3.get()), "one of referentForCatchments should not have had the data");
        Assert.notNull(keysReferenceQueue.poll(), "should return one cached element");

        Assert.isTrue(Objects.isNull(weakReferenceForCatchment1Value.get())
                | Objects.isNull(weakReferenceForCatchment2Value.get())
                | Objects.isNull(weakReferenceForCatchment3Value.get()), "one of referentForCatchmentValues should not have had the data");
        Assert.notNull(valuesReferenceQueue.poll(), "should return one cached element's value");
    }
}
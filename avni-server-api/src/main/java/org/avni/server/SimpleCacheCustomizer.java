package org.avni.server;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import static java.util.Arrays.asList;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS;

@Component
public class SimpleCacheCustomizer
        implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

    @Override
    public void customize(ConcurrentMapCacheManager cacheManager) {
        cacheManager.setCacheNames(asList(ADDRESSES_PER_CATCHMENT, ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS));
    }
}

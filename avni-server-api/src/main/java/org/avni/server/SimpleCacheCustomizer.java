package org.avni.server;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import static java.util.Arrays.asList;
import static org.avni.messaging.repository.GlificContactRepository.GLIFIC_CONTACT_FOR_PHONE_NUMBER;
import static org.avni.messaging.repository.GlificMessageTemplateRepository.GLIFIC_TEMPLATES_FOR_ORG;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT;
import static org.avni.server.service.AddressLevelCache.ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS;
import static org.avni.server.service.IndividualService.PHONE_NUMBER_FOR_SUBJECT_ID;

@Component
public class SimpleCacheCustomizer
        implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

    @Override
    public void customize(ConcurrentMapCacheManager cacheManager) {
        cacheManager.setCacheNames(asList(ADDRESSES_PER_CATCHMENT,
                ADDRESSES_PER_CATCHMENT_AND_MATCHING_ADDR_LEVELS,
                PHONE_NUMBER_FOR_SUBJECT_ID,
                GLIFIC_CONTACT_FOR_PHONE_NUMBER,
                GLIFIC_TEMPLATES_FOR_ORG));
    }
}

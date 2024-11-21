package org.avni.server.framework.hibernate;

import org.ehcache.event.CacheEvent;
import org.ehcache.event.CacheEventListener;
import org.slf4j.LoggerFactory;

public class CacheEventLogger implements CacheEventListener {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);

    @Override
    public void onEvent(CacheEvent event) {
        logger.info("Event: {}", event);
    }
}

package org.avni.server.framework.hibernate;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.slf4j.LoggerFactory;

public class CacheEventLogger implements CacheEventListener {

    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);

    @Override
    public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
        logger.debug("Element added in {} : {}", cache.getName(), element);
    }

    @Override
    public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
        logger.debug("Element added in {}: {}", cache.getName(), element);
    }

    @Override
    public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {

    }

    @Override
    public void notifyElementExpired(Ehcache cache, Element element) {
        logger.debug("Element expired in {}: {}", cache.getName(), element);
    }

    @Override
    public void notifyElementEvicted(Ehcache cache, Element element) {
        logger.debug("Element evicted in {}: {}", cache.getName(), element);
    }

    @Override
    public void notifyRemoveAll(Ehcache cache) {
    }

    @Override
    public Object clone() throws CloneNotSupportedException {
        return null;
    }

    @Override
    public void dispose() {
    }
}

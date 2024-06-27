package org.avni.server.framework.hibernate;

import net.sf.ehcache.CacheException;
import net.sf.ehcache.Ehcache;
import net.sf.ehcache.Element;
import net.sf.ehcache.event.CacheEventListener;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.LoggerFactory;

public class CacheEventLogger implements CacheEventListener {
    private static final org.slf4j.Logger logger = LoggerFactory.getLogger(CacheEventLogger.class);

    private String getCacheInfo(Ehcache ehcache) {
        return String.format("Cache name: %s, Cache size: %d", ehcache.getName(), ehcache.getSize());
    }

    private String getElementInfo(Element element) {
        return String.format("Element: %s", element.getObjectKey().toString());
    }

    private void log(String action, Ehcache cache, Element element) {
        if (logger.isTraceEnabled()) {
            logger.trace("{} -> {}, {}, User: {}", action, getCacheInfo(cache), getElementInfo(element), UserContextHolder.getUserName());
        }
    }

    @Override
    public void notifyElementRemoved(Ehcache cache, Element element) throws CacheException {
        this.log("Removed", cache, element);
    }

    @Override
    public void notifyElementPut(Ehcache cache, Element element) throws CacheException {
        this.log("Put", cache, element);
    }

    @Override
    public void notifyElementUpdated(Ehcache cache, Element element) throws CacheException {
        this.log("Updated", cache, element);
    }

    @Override
    public void notifyElementExpired(Ehcache cache, Element element) {
        this.log("Expired", cache, element);
    }

    @Override
    public void notifyElementEvicted(Ehcache cache, Element element) {
        logger.debug("Evicted -> {}, {}, User: {}", getCacheInfo(cache), getElementInfo(element), UserContextHolder.getUserName());
    }

    @Override
    public void notifyRemoveAll(Ehcache cache) {
        logger.debug("All removed. {}", getCacheInfo(cache));
    }

    @Override
    public Object clone() {
        return null;
    }

    @Override
    public void dispose() {
        logger.info("Dispose called.");
    }
}

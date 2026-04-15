package org.avni.messaging.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Binds application.properties values under the "avni.wati" prefix.
 *
 * Properties:
 *   avni.wati.orgIds         — comma-separated org IDs that use Wati instead of Glific
 *   avni.wati.platformOrgId  — org ID under which the shared Wati external_system_config row is stored
 *
 * To add a new org to Wati: append its org ID to AVNI_WATI_ORG_IDS env var. No code change needed.
 */
@Component
@ConfigurationProperties(prefix = "avni.wati")
public class WatiProperties {

    /**
     * Org IDs that should use Wati as their messaging provider.
     * Spring auto-splits the comma-separated string from properties into this list.
     */
    private List<Long> orgIds = Collections.emptyList();

    /**
     * The org ID under which the single shared Wati external_system_config row is stored.
     * This is the platform/superadmin org, not a tenant org.
     */
    private Long platformOrgId;

    public List<Long> getOrgIds() {
        return orgIds;
    }

    public void setOrgIds(List<Long> orgIds) {
        this.orgIds = orgIds;
    }

    public Long getPlatformOrgId() {
        return platformOrgId;
    }

    public void setPlatformOrgId(Long platformOrgId) {
        this.platformOrgId = platformOrgId;
    }
}

package org.avni.messaging.service;

import org.avni.messaging.config.WatiProperties;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Determines which messaging provider (Glific or Wati) should be used for the current org.
 *
 * Wati is a shared/platform-level account — one account used by multiple orgs.
 * Which orgs use Wati is controlled via avni.wati.orgIds in application.properties
 * (set via AVNI_WATI_ORG_IDS env var). This avoids per-org DB config for provider routing.
 *
 * To add a new org to Wati: append its org ID to AVNI_WATI_ORG_IDS — no code change needed.
 *
 * This resolver is injected into:
 *   - MessageReceiverService  — to decide how to resolve the contact's externalId
 *   - IndividualMessagingService — to decide which API to call for sending
 */
@Service
public class MessagingProviderResolver {

    private static final Logger logger = LoggerFactory.getLogger(MessagingProviderResolver.class);

    private final WatiProperties watiProperties;

    @Autowired
    public MessagingProviderResolver(WatiProperties watiProperties) {
        this.watiProperties = watiProperties;
    }

    /**
     * Returns true if the current org is configured to use Wati as its messaging provider.
     * Falls back to Glific if the org is not in the Wati org list or if the list is empty.
     */
    public boolean isWatiConfigured() {
        Long currentOrgId = UserContextHolder.getUserContext().getOrganisationId();
        boolean isWati = watiProperties.getOrgIds().contains(currentOrgId);
        logger.debug("Messaging provider for org {}: {}", currentOrgId, isWati ? "Wati" : "Glific");
        return isWati;
    }
}

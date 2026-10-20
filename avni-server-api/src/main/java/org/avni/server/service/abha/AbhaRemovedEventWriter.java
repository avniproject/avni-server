package org.avni.server.service.abha;

import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.User;
import org.avni.server.domain.UserContext;
import org.avni.server.domain.UserSettings;
import org.avni.server.framework.security.UserContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;

// The server's own "removed" record, written when a save or void takes an ABHA off a patient. Kept apart from
// AbhaEventService, which needs AbhaService, so that AbhaLinkService can use it without a dependency cycle.
@Service
public class AbhaRemovedEventWriter {
    private static final Logger logger = LoggerFactory.getLogger(AbhaRemovedEventWriter.class);
    private final AbhaEventRepository abhaEventRepository;

    @Autowired
    public AbhaRemovedEventWriter(AbhaEventRepository abhaEventRepository) {
        this.abhaEventRepository = abhaEventRepository;
    }

    // Names the user whose save carried the removal, with their health centre, or "" when they have none.
    public void recordRemoved(String subjectUuid) {
        UserContext userContext = UserContextHolder.getUserContext();
        User user = userContext.getUser();
        String hipId = user.getSettings() == null ? null : user.getSettings().getString(UserSettings.ABHA_HIP_ID);
        AbhaEvent event = new AbhaEvent();
        event.assignUUID();
        event.setOrganisationId(userContext.getOrganisationId());
        event.setSubjectUuid(subjectUuid);
        event.setUserId(user.getId());
        event.setHipId(hipId == null ? "" : hipId);
        event.setAction(AbhaEventService.REMOVED);
        event.setRoute(AbhaEventService.UNKNOWN_ROUTE);
        event.setOccurredAt(Instant.now());
        abhaEventRepository.save(event);
        logger.info("ABHA event {} recorded, action {}", event.getUuid(), event.getAction());
    }
}

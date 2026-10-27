package org.avni.server.service.abha;

import org.avni.server.dao.AbhaEventRepository;
import org.avni.server.dao.IndividualRepository;
import org.avni.server.domain.AbhaEvent;
import org.avni.server.domain.Individual;
import org.avni.server.domain.User;
import org.avni.server.domain.accessControl.AvniAccessException;
import org.avni.server.domain.accessControl.PrivilegeType;
import org.avni.server.service.accessControl.AccessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

// A patient's ABHA card, fetched from Eka on every request and kept nowhere on the server: the card is a picture of
// the patient's identity. Nothing here logs the card, the organisation's Eka token or the patient's Eka ID.
@Service
public class AbhaCardService {
    private static final Logger logger = LoggerFactory.getLogger(AbhaCardService.class);
    // A registration the phone has not synced yet: its card goes only to the user whose ABHA record reached the server
    // this recently, by the server's clock.
    static final Duration RECORDED_WITHIN = Duration.ofMinutes(30);

    private final AbhaService abhaService;
    private final IndividualRepository individualRepository;
    private final AccessControlService accessControlService;
    private final AbhaEventRepository abhaEventRepository;
    private final EkaRestClient ekaRestClient;

    @Autowired
    public AbhaCardService(AbhaService abhaService, IndividualRepository individualRepository, AccessControlService accessControlService,
                           AbhaEventRepository abhaEventRepository, EkaRestClient ekaRestClient) {
        this.abhaService = abhaService;
        this.individualRepository = individualRepository;
        this.accessControlService = accessControlService;
        this.abhaEventRepository = abhaEventRepository;
        this.ekaRestClient = ekaRestClient;
    }

    public byte[] getCard(String subjectUUID) {
        return getCard(subjectUUID, null);
    }

    // In order: ABHA on, Eka settings stored and the user's health centre (as /abha/session); then the user's right to
    // this patient; then an ABHA with an Eka ID recorded for them; then Eka. abhaLastFour: the ABHA the phone shows the
    // card for, the record's on the profile or the one just found in a registration or edit.
    public byte[] getCard(String subjectUUID, String abhaLastFour) {
        AbhaAccess access = abhaService.checkOrganisationAccess();
        AbhaEvent event = cardRecord(recordsTheUserMaySee(subjectUUID, access.getUser()), abhaLastFour)
                .orElseThrow(() -> new AbhaRefusedException(AbhaRefusal.ABHA_NO_CARD));
        EkaToken token = abhaService.ekaAccessToken(access.getOrganisationId());
        try {
            byte[] card = ekaRestClient.getAbhaCard(access.getEkaSystemConfig().getBaseUrl(), token.getAccessToken(), event.getEkaOid(), access.getHipId());
            logger.info("ABHA card of subject {} fetched from Eka for user {} of organisation {}, {} bytes",
                    subjectUUID, access.getUser().getId(), access.getOrganisationId(), card.length);
            return card;
        } catch (EkaSessionExpiredException e) {
            logger.info("ABHA card of subject {} not fetched from Eka for user {} of organisation {}: the patient's ABHA login at Eka has lapsed",
                    subjectUUID, access.getUser().getId(), access.getOrganisationId());
            throw new AbhaRefusedException(AbhaRefusal.ABHA_SESSION_EXPIRED);
        } catch (EkaCardException e) {
            logger.warn("ABHA card of subject {} not fetched from Eka for user {} of organisation {}: {}",
                    subjectUUID, access.getUser().getId(), access.getOrganisationId(), e.getMessage());
            throw new AbhaRefusedException(AbhaRefusal.ABHA_EKA_UNAVAILABLE);
        }
    }

    // None after a removal. Without abhaLastFour (an older app), the card of the latest record, and none when it has no
    // Eka ID rather than the card of the ABHA before it. With it, the latest record of that ABHA with an Eka ID: a later
    // lookup of another ABHA, left unsaved by a worker, never puts that person's card on this patient's profile.
    static Optional<AbhaEvent> cardRecord(List<AbhaEvent> newestFirst, String abhaLastFour) {
        String wanted = abhaLastFour != null && abhaLastFour.matches("[0-9]{4}") ? abhaLastFour : null;
        for (AbhaEvent record : newestFirst) {
            if (AbhaEventService.REMOVED.equals(record.getAction())) return Optional.empty();
            if (wanted == null) return StringUtils.hasText(record.getEkaOid()) ? Optional.of(record) : Optional.empty();
            if (AbhaLinkService.digitsOnly(record.getAbhaNumber()).endsWith(wanted) && StringUtils.hasText(record.getEkaOid())) {
                return Optional.of(record);
            }
        }
        return Optional.empty();
    }

    // A patient on the server: the user's right to view their subject type, and the patient within what the user's
    // phone may hold (catchment, direct assignment, sync attributes); then the latest record the server received for
    // them, from whichever worker. A patient the server does not have, or cannot show this organisation: only the user's
    // own latest record, received in the last 30 minutes, straight after a registration that has not synced. Anyone may
    // post a record naming a UUID the server does not have, so another worker's record for it is never served; and
    // without the 30 minutes anyone in the organisation who learnt a patient's ID could fetch their card.
    private List<AbhaEvent> recordsTheUserMaySee(String subjectUUID, User user) {
        Individual individual = StringUtils.hasText(subjectUUID) ? individualRepository.findByUuid(subjectUUID) : null;
        if (individual == null) {
            List<AbhaEvent> own = abhaEventRepository.findBySubjectUuidAndUserIdAndCreatedDateTimeAfterOrderByCreatedDateTimeDescIdDesc(
                    subjectUUID, user.getId(), Date.from(Instant.now().minus(RECORDED_WITHIN)));
            if (own.isEmpty()) {
                throw new AbhaRefusedException(AbhaRefusal.ABHA_NOT_YOURS);
            }
            return own;
        }
        try {
            accessControlService.checkSubjectPrivilege(PrivilegeType.ViewSubject, individual);
        } catch (AvniAccessException e) {
            throw new AbhaRefusedException(AbhaRefusal.ABHA_NO_PRIVILEGE);
        }
        abhaService.checkSubjectWithinReach(individual);
        return abhaEventRepository.findBySubjectUuidOrderByCreatedDateTimeDescIdDesc(subjectUUID);
    }
}

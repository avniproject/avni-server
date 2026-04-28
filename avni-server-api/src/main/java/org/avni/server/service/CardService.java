package org.avni.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.domain.util.EntityUtil;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.domain.ValueUnit;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.avni.server.domain.StandardReportCardTypeType.OverdueVisits;
import static org.avni.server.domain.StandardReportCardTypeType.ScheduledVisits;
import static org.avni.server.mapper.dashboard.DefaultDashboardConstants.*;

@Service
public class CardService implements NonScopeAwareService {
    private final CardRepository cardRepository;
    private final StandardReportCardTypeRepository standardReportCardTypeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;
    private final CustomCardConfigRepository customCardConfigRepository;
    private final CustomCardConfigService customCardConfigService;

    @Autowired
    public CardService(CardRepository cardRepository, StandardReportCardTypeRepository standardReportCardTypeRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository, CustomCardConfigRepository customCardConfigRepository, CustomCardConfigService customCardConfigService) {
        this.cardRepository = cardRepository;
        this.standardReportCardTypeRepository = standardReportCardTypeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
        this.customCardConfigRepository = customCardConfigRepository;
        this.customCardConfigService = customCardConfigService;
    }

    public ReportCard saveCard(ReportCardWebRequest reportCardRequest) {
        assertNoExistingCardWithName(reportCardRequest.getName());
        ReportCard card = new ReportCard();
        card.assignUUID();
        buildCard(reportCardRequest, card);
        buildStandardReportCardType(reportCardRequest, card);
        buildAction(reportCardRequest.getAction(), card);
        buildActionDetail(card, reportCardRequest.getActionDetailSubjectTypeUUID(), reportCardRequest.getActionDetailProgramUUID(), reportCardRequest.getActionDetailEncounterTypeUUID(), reportCardRequest.getActionDetailVisitType());
        linkCustomCardConfig(card, reportCardRequest);
        cardRepository.save(card);
        return card;
    }

    public void uploadCard(ReportCardBundleRequest reportCardRequest) {
        ReportCard card = cardRepository.findByUuid(reportCardRequest.getUuid());
        if (card == null) {
            card = new ReportCard();
            card.setUuid(reportCardRequest.getUuid());
        }
        buildCard(reportCardRequest, card);
        buildStandardReportCardType(reportCardRequest, card);
        buildAction(reportCardRequest.getAction(), card);
        buildActionDetail(card, reportCardRequest.getActionDetailSubjectTypeUUID(), reportCardRequest.getActionDetailProgramUUID(), reportCardRequest.getActionDetailEncounterTypeUUID(), reportCardRequest.getActionDetailVisitType());
        upsertAndLinkCustomCardConfig(card, reportCardRequest.getCustomCardConfig());
        cardRepository.save(card);
    }

    public ReportCard editCard(ReportCardWebRequest request, Long cardId) {
        ReportCard existingCard = cardRepository.findOne(cardId);
        assertNewNameIsUnique(request.getName(), existingCard.getName());
        buildCard(request, existingCard);
        buildStandardReportCardType(request, existingCard);
        buildAction(request.getAction(), existingCard);
        buildActionDetail(existingCard, request.getActionDetailSubjectTypeUUID(), request.getActionDetailProgramUUID(), request.getActionDetailEncounterTypeUUID(), request.getActionDetailVisitType());
        linkCustomCardConfig(existingCard, request);
        return cardRepository.save(existingCard);
    }

    public void deleteCard(Long id) {
        ReportCard card = cardRepository.findEntity(id);
        card.setVoided(true);
        card.setName(EntityUtil.getVoidedName(card.getName(), card.getId()));
        cardRepository.save(card);
    }

    public List<ReportCard> getAll() {
        return cardRepository.findAll();
    }

    private void buildStandardReportCardType(ReportCardWebRequest reportCardWebRequest, ReportCard reportCard) {
        Long standardReportCardTypeId = reportCardWebRequest.getStandardReportCardTypeId();

        if (standardReportCardTypeId != null) {
            StandardReportCardType type = standardReportCardTypeRepository.findById(standardReportCardTypeId).orElse(null);
            if (type == null) {
                throw new BadRequestError(String.format("StandardReportCardType with id %d doesn't exist", standardReportCardTypeId));
            }
            reportCard.setStandardReportCardType(type);
            buildStandardReportCardInputs(type, reportCardWebRequest.getStandardReportCardInputSubjectTypes(),
                    reportCardWebRequest.getStandardReportCardInputPrograms(),
                    reportCardWebRequest.getStandardReportCardInputEncounterTypes(), reportCardWebRequest.getStandardReportCardInputRecentDuration(), reportCard);
        } else {
            reportCard.resetStandardReportCardInput();
            reportCard.setStandardReportCardType(null);
        }
    }

    private void buildStandardReportCardType(ReportCardBundleRequest reportCardBundleRequest, ReportCard reportCard) {
        String standardReportCardTypeUUID = reportCardBundleRequest.getStandardReportCardType();

        if (standardReportCardTypeUUID != null) {
            StandardReportCardType type = standardReportCardTypeRepository.findByUuid(standardReportCardTypeUUID);
            if (type == null) {
                throw new BadRequestError(String.format("StandardReportCardType with uuid %s doesn't exist", standardReportCardTypeUUID));
            }
            reportCard.setStandardReportCardType(type);
            ValueUnit recentDuration = buildDurationForRecentTypeCards(reportCardBundleRequest.getStandardReportCardInputRecentDuration());
            buildStandardReportCardInputs(type, reportCardBundleRequest.getStandardReportCardInputSubjectTypes(), reportCardBundleRequest.getStandardReportCardInputPrograms(), reportCardBundleRequest.getStandardReportCardInputEncounterTypes(), recentDuration, reportCard);
        } else {
            reportCard.setStandardReportCardType(null);
        }
    }

    private void buildStandardReportCardInputs(StandardReportCardType type, List<String> subjectTypes, List<String> programs, List<String> encounterTypes, ValueUnit recentDuration, ReportCard card) {
        if (!type.getType().isInputStandardReportCardType()) {
            card.resetStandardReportCardInput();
            return;
        }

        card.setStandardReportCardInputSubjectTypes(subjectTypes);
        card.setStandardReportCardInputPrograms(programs);
        card.setStandardReportCardInputEncounterTypes(encounterTypes);

        buildStandardReportCardInputRecentDuration(type, recentDuration, card);
    }

    private void buildStandardReportCardInputRecentDuration(StandardReportCardType type, ValueUnit recentDuration, ReportCard card) {
        if (!type.getType().isRecentStandardReportCardType()) {
            card.resetStandardReportCardInputRecentDuration();
            return;
        }
        if (recentDuration == null) {
            throw new BadRequestError("Recent Duration required for Recent type Standard Report cards");
        }
        card.setStandardReportCardInputRecentDuration(recentDuration);
    }

    private void buildCard(ReportCardContract reportCardRequest, ReportCard card) {
        card.setName(reportCardRequest.getName());
        card.setColour(reportCardRequest.getColor());
        card.setDescription(reportCardRequest.getDescription());
        card.setQuery(StringUtils.hasText(reportCardRequest.getQuery()) ? reportCardRequest.getQuery() : null);
        card.setVoided(reportCardRequest.isVoided());
        card.setIconFileS3Key(reportCardRequest.getIconFileS3Key());

        card.setNested(reportCardRequest.isNested());
        if (reportCardRequest.getCount() < ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS || reportCardRequest.getCount() > ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS) {
            throw new BadRequestError(String.format("Nested ReportCard count should have minmum value of %d and maximum value of %d",
                    ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS, ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS));
        }
        card.setCountOfCards(reportCardRequest.getCount());
        buildOnActionCompletion(reportCardRequest.getOnActionCompletion(), card);
    }

    private void buildAction(String action, ReportCard card) {
        if (action == null) {
            card.setAction(null);
            return;
        }
        try {
            card.setAction(ReportCardAction.valueOf(action));
        } catch (IllegalArgumentException e) {
            throw new BadRequestError(String.format("Invalid action '%s'. Allowed values: %s", action, Arrays.toString(ReportCardAction.values())));
        }
    }

    private void buildOnActionCompletion(String onActionCompletion, ReportCard card) {
        if (onActionCompletion == null) {
            card.setOnActionCompletion(null);
            return;
        }
        try {
            card.setOnActionCompletion(ReportCardActionCompletion.valueOf(onActionCompletion));
        } catch (IllegalArgumentException e) {
            throw new BadRequestError(String.format("Invalid onActionCompletion '%s'. Allowed values: %s", onActionCompletion, Arrays.toString(ReportCardActionCompletion.values())));
        }
    }

    private void linkCustomCardConfig(ReportCard card, ReportCardWebRequest request) {
        CustomCardConfigRequest configRequest = request.getCustomCardConfig();
        if (configRequest != null) {
            upsertAndLinkCustomCardConfig(card, configRequest);
            return;
        }
        buildCustomCardConfig(card, request.getCustomCardConfigUUID());
    }

    private void buildCustomCardConfig(ReportCard card, String customCardConfigUUID) {
        if (!StringUtils.hasText(customCardConfigUUID)) {
            card.setCustomCardConfig(null);
            return;
        }
        CustomCardConfig config = customCardConfigRepository.findByUuid(customCardConfigUUID);
        if (config == null) {
            throw new BadRequestError(String.format("CustomCardConfig with uuid %s doesn't exist", customCardConfigUUID));
        }
        card.setCustomCardConfig(config);
    }

    private void buildActionDetail(ReportCard card, String subjectTypeUUID, String programUUID, String encounterTypeUUID, String visitType) {
        if (card.getAction() != ReportCardAction.DoVisit) {
            card.setActionDetail(null);
            return;
        }
        if (!StringUtils.hasText(subjectTypeUUID)) {
            throw new BadRequestError("Subject type is required when action is DoVisit");
        }
        if (subjectTypeRepository.findByUuid(subjectTypeUUID) == null) {
            throw new BadRequestError(String.format("SubjectType with uuid %s doesn't exist", subjectTypeUUID));
        }
        if (StringUtils.hasText(programUUID) && programRepository.findByUuid(programUUID) == null) {
            throw new BadRequestError(String.format("Program with uuid %s doesn't exist", programUUID));
        }
        if (!StringUtils.hasText(encounterTypeUUID)) {
            throw new BadRequestError("Encounter type is required when action is DoVisit");
        }
        if (encounterTypeRepository.findByUuid(encounterTypeUUID) == null) {
            throw new BadRequestError(String.format("EncounterType with uuid %s doesn't exist", encounterTypeUUID));
        }
        if (!StringUtils.hasText(visitType)) {
            throw new BadRequestError("Visit type is required when action is DoVisit");
        }
        card.setActionDetailFields(subjectTypeUUID, programUUID, encounterTypeUUID, visitType);
    }

    public ValueUnit buildDurationForRecentTypeCards(String recentDurationString) {
        if (!StringUtils.hasText(recentDurationString)) {
            return ValueUnit.getDefaultRecentDuration();
        }
        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(recentDurationString, ValueUnit.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    private void assertNewNameIsUnique(String newName, String oldName) {
        if (!newName.trim().equalsIgnoreCase(oldName.trim())) {
            assertNoExistingCardWithName(newName);
        }
    }

    private void assertNoExistingCardWithName(String name) {
        List<ReportCard> existingCards = cardRepository.findByNameIgnoreCaseAndIsVoidedFalse(name);
        if (existingCards != null && existingCards.size() > 0) {
            throw new BadRequestError(String.format("Report card with same name (%s) already exists", name));
        }
    }

    public List<SubjectType> getStandardReportCardInputSubjectTypes(ReportCard card) {
        return subjectTypeRepository.findAllByUuidIn(card.getStandardReportCardInputSubjectTypes());
    }

    public List<Program> getStandardReportCardInputPrograms(ReportCard card) {
        return programRepository.findAllByUuidIn(card.getStandardReportCardInputPrograms());
    }

    public List<EncounterType> getStandardReportCardInputEncounterTypes(ReportCard card) {
        return encounterTypeRepository.findAllByUuidIn(card.getStandardReportCardInputEncounterTypes());
    }

    private void upsertAndLinkCustomCardConfig(ReportCard card, CustomCardConfigRequest configRequest) {
        if (configRequest == null) {
            card.setCustomCardConfig(null);
            return;
        }
        CustomCardConfig config = customCardConfigService.createOrUpdateCustomCardConfig(configRequest);
        card.setCustomCardConfig(config);
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return cardRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    public void saveCards(ReportCardBundleRequest[] cardContracts) {
        for (ReportCardBundleRequest cardContract : cardContracts) {
            try {
                uploadCard(cardContract);
            } catch (Exception e) {
                throw new BulkItemSaveException(cardContract, e);
            }
        }
    }

    public Map<StandardReportCardTypeType, ReportCard> createDefaultDashboardCards(Organisation organisation) {
        Map<StandardReportCardTypeType, String> defaultDashboardCards = CARD_TYPE_UUID_MAPPING;
        List<StandardReportCardType> standardReportCardTypes = standardReportCardTypeRepository.findAllByTypeIn(defaultDashboardCards.keySet());
        Map<StandardReportCardTypeType, ReportCard> savedCards = new HashMap<>();
        standardReportCardTypes.forEach(standardReportCardType -> {
            ReportCard reportCard = new ReportCard();
            reportCard.setUuid(defaultDashboardCards.get(standardReportCardType.getType()));
            reportCard.setStandardReportCardType(standardReportCardType);
            reportCard.setOrganisationId(organisation.getId());
            reportCard.setName(standardReportCardType.getDescription());
            reportCard.setColour(WHITE_BG_COLOUR);
            if (standardReportCardType.getType().isInputStandardReportCardType()) {
                reportCard.setStandardReportCardInputPrograms(Collections.emptyList());
                reportCard.setStandardReportCardInputEncounterTypes(Collections.emptyList());
                reportCard.setStandardReportCardInputSubjectTypes(Collections.emptyList());
            }
            if (standardReportCardType.getType().isRecentStandardReportCardType()) {
                reportCard.setStandardReportCardInputRecentDuration(ValueUnit.getDefaultRecentDuration());
            }
            if (standardReportCardType.getType().equals(OverdueVisits)) {
                reportCard.setColour(RED_BG_COLOUR);
            }
            if (standardReportCardType.getType().equals(ScheduledVisits)) {
                reportCard.setColour(GREEN_BG_COLOUR);
            }
            savedCards.put(standardReportCardType.getType(), cardRepository.save(reportCard));
        });
        return savedCards;
    }
}

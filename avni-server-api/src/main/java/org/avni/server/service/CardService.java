package org.avni.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.avni.server.common.BulkItemSaveException;
import org.avni.server.dao.*;
import org.avni.server.domain.*;
import org.avni.server.util.BadRequestError;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.contract.ValueUnit;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.avni.server.web.request.reports.ReportCardWebRequest;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CardService implements NonScopeAwareService {
    private final CardRepository cardRepository;
    private final StandardReportCardTypeRepository standardReportCardTypeRepository;
    private final SubjectTypeRepository subjectTypeRepository;
    private final ProgramRepository programRepository;
    private final EncounterTypeRepository encounterTypeRepository;

    @Autowired
    public CardService(CardRepository cardRepository, StandardReportCardTypeRepository standardReportCardTypeRepository, SubjectTypeRepository subjectTypeRepository, ProgramRepository programRepository, EncounterTypeRepository encounterTypeRepository) {
        this.cardRepository = cardRepository;
        this.standardReportCardTypeRepository = standardReportCardTypeRepository;
        this.subjectTypeRepository = subjectTypeRepository;
        this.programRepository = programRepository;
        this.encounterTypeRepository = encounterTypeRepository;
    }

    public ReportCard saveCard(ReportCardWebRequest reportCardRequest) {
        assertNoExistingCardWithName(reportCardRequest.getName());
        ReportCard card = new ReportCard();
        card.assignUUID();
        buildCard(reportCardRequest, card);
        buildStandardReportCardType(reportCardRequest, card);
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
        cardRepository.save(card);
    }

    public ReportCard editCard(ReportCardWebRequest request, Long cardId) {
        ReportCard existingCard = cardRepository.findOne(cardId);
        assertNewNameIsUnique(request.getName(), existingCard.getName());
        buildCard(request, existingCard);
        buildStandardReportCardType(request, existingCard);
        return cardRepository.save(existingCard);
    }

    public void deleteCard(ReportCard card) {
        card.setVoided(true);
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
        card.setStandardReportCardInputSubjectTypes(subjectTypes);
        card.setStandardReportCardInputPrograms(programs);
        card.setStandardReportCardInputEncounterTypes(encounterTypes);

        if (isRecentStandardReportCard(type.getName())) {
            if (recentDuration == null) {
                throw new BadRequestError("Recent Duration required for Recent type Standard Report cards");
            }
            card.setStandardReportCardInputRecentDuration(recentDuration);
        }
    }

    private boolean isRecentStandardReportCard(String cardTypeName) {
        return cardTypeName.toLowerCase().contains("recent");
    }

    private void buildCard(ReportCardContract reportCardRequest, ReportCard card) {
        card.setName(reportCardRequest.getName());
        card.setColour(reportCardRequest.getColor());
        card.setDescription(reportCardRequest.getDescription());
        card.setQuery(reportCardRequest.getQuery());
        card.setVoided(reportCardRequest.isVoided());
        card.setIconFileS3Key(reportCardRequest.getIconFileS3Key());

        card.setNested(reportCardRequest.isNested());
        if (reportCardRequest.getCount() < ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS || reportCardRequest.getCount() > ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS) {
            throw new BadRequestError(String.format("Nested ReportCard count should have minmum value of %d and maximum value of %d",
                    ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS, ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS));
        }
        card.setCountOfCards(reportCardRequest.getCount());
    }

    public ValueUnit buildDurationForRecentTypeCards(String recentDurationString) {
        if (recentDurationString == null) return null;
        try {
            return ObjectMapperSingleton.getObjectMapper().readValue(recentDurationString, ValueUnit.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

    }

    private void assertNewNameIsUnique(String newName, String oldName) {
        if (!newName.equals(oldName)) {
            assertNoExistingCardWithName(newName);
        }
    }

    private void assertNoExistingCardWithName(String name) {
        ReportCard existingCard = cardRepository.findByName(name);
        if (existingCard != null) {
            throw new BadRequestError(String.format("Card %s already exists", name));
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

    public Map<String, ReportCard> createDefaultDashboardCards(Organisation organisation) {
        Map<String, String> defaultDashboardCards = getDefaultDashboardCardNamesAndUuids();
        List<StandardReportCardType> standardReportCardTypes = standardReportCardTypeRepository.findAllByNameIn(defaultDashboardCards.keySet());
        Map<String, ReportCard> savedCards = new HashMap<>();
        standardReportCardTypes.forEach(standardReportCardType -> {
            ReportCard reportCard = new ReportCard();
            reportCard.setUuid(defaultDashboardCards.get(standardReportCardType.getName()));
            reportCard.setStandardReportCardType(standardReportCardType);
            reportCard.setOrganisationId(organisation.getId());
            reportCard.setName(standardReportCardType.getName());
            reportCard.setColour("#ffffff");
            reportCard.setStandardReportCardInputPrograms(Collections.emptyList());
            reportCard.setStandardReportCardInputEncounterTypes(Collections.emptyList());
            reportCard.setStandardReportCardInputSubjectTypes(Collections.emptyList());
            if (isRecentStandardReportCard(standardReportCardType.getName())) {
                reportCard.setStandardReportCardInputRecentDuration(new ValueUnit("1", "days"));
            }
            if (standardReportCardType.getName().equals("Overdue visits")) {
                reportCard.setColour("#d32f2f");
            }
            if (standardReportCardType.getName().equals("Scheduled visits")) {
                reportCard.setColour("#388e3c");
            }
            savedCards.put(reportCard.getName(), cardRepository.save(reportCard));
        });
        return savedCards;
    }

    private static Map<String, String> getDefaultDashboardCardNamesAndUuids() {
        Map<String, String> defaultDashboardStandardCardTypeNamesAndUuids = new HashMap<>();
        defaultDashboardStandardCardTypeNamesAndUuids.put("Scheduled visits", "6085c2f4-52e7-4b08-85b6-d6b2612b4cf5");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Overdue visits", "85ce7239-e8b5-4e57-b07d-66c18cee47b2");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Total", "a1673f8a-c394-4bcf-8b6f-63d83a5443e2");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Recent registrations", "f366f35a-5c4f-4ff7-b510-2dc9f5f88847");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Recent enrolments", "e1036b69-df46-4351-9916-10cd4cfcb6bd");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Recent visits", "dd961ee1-9d4e-4ec9-99f0-99b36672be7c");
        defaultDashboardStandardCardTypeNamesAndUuids.put("Due checklist", "9b7632dd-4e98-429a-8e42-67a947bf9ece");
        return defaultDashboardStandardCardTypeNamesAndUuids;
    }
}

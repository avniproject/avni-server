package org.avni.server.service;

import org.avni.server.dao.CardRepository;
import org.avni.server.dao.StandardReportCardTypeRepository;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.StandardReportCardType;
import org.avni.server.util.BadRequestError;
import org.avni.server.web.request.CardContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import org.joda.time.DateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class CardService implements NonScopeAwareService {

    private final CardRepository cardRepository;
    private final StandardReportCardTypeRepository standardReportCardTypeRepository;

    @Autowired
    public CardService(CardRepository cardRepository, StandardReportCardTypeRepository standardReportCardTypeRepository) {
        this.cardRepository = cardRepository;
        this.standardReportCardTypeRepository = standardReportCardTypeRepository;
    }

    public ReportCard saveCard(CardContract cardContract) {
        assertNoExistingCardWithName(cardContract.getName());
        ReportCard card = new ReportCard();
        card.assignUUID();
        buildCard(cardContract, card);
        cardRepository.save(card);
        return card;
    }

    public void uploadCard(CardContract cardContract) {
        ReportCard card = cardRepository.findByUuid(cardContract.getUuid());
        if (card == null) {
            card = new ReportCard();
            card.setUuid(cardContract.getUuid());
        }
        buildCard(cardContract, card);
        cardRepository.save(card);
    }

    public ReportCard editCard(CardContract newCard, Long cardId) {
        ReportCard existingCard = cardRepository.findOne(cardId);
        assertNewNameIsUnique(newCard.getName(), existingCard.getName());
        buildCard(newCard, existingCard);
        return cardRepository.save(existingCard);
    }

    public void deleteCard(ReportCard card) {
        card.setVoided(true);
        cardRepository.save(card);
    }

    public List<CardContract> getAll() {
        List<ReportCard> reportCards = cardRepository.findAll();
        return reportCards.stream().map(CardContract::fromEntity).collect(Collectors.toList());
    }

    private void buildCard(CardContract cardContract, ReportCard card) {
        card.setName(cardContract.getName());
        card.setColour(cardContract.getColor());
        card.setDescription(cardContract.getDescription());
        card.setQuery(cardContract.getQuery());
        card.setVoided(cardContract.isVoided());
        card.setIconFileS3Key(cardContract.getIconFileS3Key());
        Long standardReportCardTypeId = cardContract.getStandardReportCardTypeId();

        if (standardReportCardTypeId != null) {
            StandardReportCardType type = standardReportCardTypeRepository.findById(standardReportCardTypeId).orElse(null);
            if (type == null) {
                throw new BadRequestError(String.format("StandardReportCardType with id %d doesn't exist", standardReportCardTypeId));
            }
            card.setStandardReportCardType(type);
        } else {
            card.setStandardReportCardType(null);
        }
        card.setNested(cardContract.isNested());
        if (cardContract.getCount() < ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS || cardContract.getCount() > ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS) {
            throw new BadRequestError(String.format("Nested ReportCard count should have minmum value of %d and maximum value of %d",
                    ReportCard.INT_CONSTANT_DEFAULT_COUNT_OF_CARDS, ReportCard.INT_CONSTANT_MAX_COUNT_OF_CARDS));
        }
        card.setCountOfCards(cardContract.getCount());
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

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return cardRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }
}

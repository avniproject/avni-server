package org.avni.server.mapper.dashboard;

import org.avni.server.domain.ReportCard;
import org.avni.server.service.CardService;
import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.request.CardContract;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

@Component
public class ReportCardMapper {
    private final CardService reportCardService;

    @Autowired
    public ReportCardMapper(CardService reportCardService) {
        this.reportCardService = reportCardService;
    }

    public CardContract fromEntity(ReportCard card) {
        CardContract cardContract = new CardContract();
        cardContract.setId(card.getId());
        cardContract.setUuid(card.getUuid());
        cardContract.setVoided(card.isVoided());
        cardContract.setName(card.getName());
        cardContract.setQuery(card.getQuery());
        cardContract.setDescription(card.getDescription());
        cardContract.setColor(card.getColour());
        if (card.getStandardReportCardType() != null)
            cardContract.setStandardReportCardType(StandardReportCardTypeContract.fromEntity(card.getStandardReportCardType()));
        cardContract.setIconFileS3Key(card.getIconFileS3Key());
        cardContract.setNested(card.isNested());
        cardContract.setCount(card.getCountOfCards());
        cardContract.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(card).stream().map(SubjectTypeContract::createBasic).collect(Collectors.toList()));
        cardContract.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(card).stream().map(ProgramContract::createBasic).collect(Collectors.toList()));
        cardContract.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(card).stream().map(EncounterTypeContract::createBasic).collect(Collectors.toList()));
        return cardContract;
    }
}

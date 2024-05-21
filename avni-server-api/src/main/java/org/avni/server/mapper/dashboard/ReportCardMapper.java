package org.avni.server.mapper.dashboard;

import org.avni.server.domain.ReportCard;
import org.avni.server.service.CardService;
import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.response.ReportCardResponse;
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

    public ReportCardResponse fromEntity(ReportCard card) {
        ReportCardResponse response = new ReportCardResponse();
        response.setId(card.getId());
        response.setUuid(card.getUuid());
        response.setVoided(card.isVoided());
        response.setName(card.getName());
        response.setQuery(card.getQuery());
        response.setDescription(card.getDescription());
        response.setColor(card.getColour());
        if (card.getStandardReportCardType() != null)
            response.setStandardReportCardType(StandardReportCardTypeContract.fromEntity(card.getStandardReportCardType()));
        response.setIconFileS3Key(card.getIconFileS3Key());
        response.setNested(card.isNested());
        response.setCount(card.getCountOfCards());
        response.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(card).stream().map(SubjectTypeContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(card).stream().map(ProgramContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(card).stream().map(EncounterTypeContract::createBasic).collect(Collectors.toList()));
        return response;
    }
}

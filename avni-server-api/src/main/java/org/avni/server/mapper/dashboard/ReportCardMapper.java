package org.avni.server.mapper.dashboard;

import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.ReportCard;
import org.avni.server.service.CardService;
import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.response.reports.ReportCardBundleResponse;
import org.avni.server.web.response.reports.ReportCardResponse;
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

    public ReportCardResponse toWebResponse(ReportCard card) {
        ReportCardResponse response = new ReportCardResponse();
        setPrimitives(card, response);
        if (card.getStandardReportCardType() != null)
            response.setStandardReportCardType(StandardReportCardTypeContract.fromEntity(card.getStandardReportCardType()));
        response.setIconFileS3Key(card.getIconFileS3Key());
        response.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(card).stream().map(SubjectTypeContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(card).stream().map(ProgramContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(card).stream().map(EncounterTypeContract::createBasic).collect(Collectors.toList()));
        return response;
    }

    private void setPrimitives(ReportCard card, ReportCardContract contract) {
        contract.setId(card.getId());
        contract.setUuid(card.getUuid());
        contract.setVoided(card.isVoided());
        contract.setName(card.getName());
        contract.setQuery(card.getQuery());
        contract.setDescription(card.getDescription());
        contract.setColor(card.getColour());
        contract.setNested(card.isNested());
        contract.setCount(card.getCountOfCards());
    }

    public ReportCardBundleResponse toBundleResponse(ReportCard reportCard) {
        ReportCardBundleResponse response = new ReportCardBundleResponse();
        setPrimitives(reportCard, response);
        if (reportCard.getStandardReportCardType() != null) {
            response.setStandardReportCardType(reportCard.getStandardReportCardType().getUuid());
        }
        response.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        response.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        response.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        return response;
    }
}

package org.avni.server.mapper.dashboard;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.domain.CHSBaseEntity;
import org.avni.server.domain.ReportCard;
import org.avni.server.service.CardService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.contract.EncounterTypeContract;
import org.avni.server.web.contract.ProgramContract;
import org.avni.server.web.contract.ReportCardContract;
import org.avni.server.web.contract.ValueUnit;
import org.avni.server.web.request.StandardReportCardTypeContract;
import org.avni.server.web.request.SubjectTypeContract;
import org.avni.server.web.response.reports.ReportCardBundleContract;
import org.avni.server.web.response.reports.ReportCardWebResponse;
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

    public ReportCardWebResponse toWebResponse(ReportCard card) {
        ReportCardWebResponse response = new ReportCardWebResponse();
        setPrimitives(card, response);
        if (card.getStandardReportCardType() != null)
            response.setStandardReportCardType(StandardReportCardTypeContract.fromEntity(card.getStandardReportCardType()));
        response.setIconFileS3Key(card.getIconFileS3Key());
        response.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(card).stream().map(SubjectTypeContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(card).stream().map(ProgramContract::createBasic).collect(Collectors.toList()));
        response.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(card).stream().map(EncounterTypeContract::createBasic).collect(Collectors.toList()));
        if (card.getStandardReportCardInputRecentDuration() != null) {
            response.setStandardReportCardInputRecentDuration(buildDurationForRecentTypeCards(card.getStandardReportCardInputRecentDuration()));
        }
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

    public ReportCardBundleContract toBundle(ReportCard reportCard) {
        ReportCardBundleContract response = new ReportCardBundleContract();
        setPrimitives(reportCard, response);
        if (reportCard.getStandardReportCardType() != null) {
            response.setStandardReportCardType(reportCard.getStandardReportCardType().getUuid());
        }
        response.setStandardReportCardInputSubjectTypes(reportCardService.getStandardReportCardInputSubjectTypes(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        response.setStandardReportCardInputPrograms(reportCardService.getStandardReportCardInputPrograms(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        response.setStandardReportCardInputEncounterTypes(reportCardService.getStandardReportCardInputEncounterTypes(reportCard).stream().map(CHSBaseEntity::getUuid).collect(Collectors.toList()));
        if (reportCard.getStandardReportCardInputRecentDuration() != null) {
            response.setStandardReportCardInputRecentDuration(buildDurationForRecentTypeCards(reportCard.getStandardReportCardInputRecentDuration()));
        }
        return response;
    }

    private ValueUnit buildDurationForRecentTypeCards(String recentDurationString) {
        try {
            ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
            return objectMapper.readValue(recentDurationString, ValueUnit.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}

package org.avni.server.mapper.dashboard;

import org.avni.server.domain.CustomCardConfig;
import org.avni.server.domain.ReportCard;
import org.avni.server.service.CardService;
import org.avni.server.web.request.CustomCardConfigRequest;
import org.avni.server.web.response.reports.ReportCardBundleContract;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Collections;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class ReportCardMapperTest {
    private ReportCardMapper mapper;
    private CardService cardService;

    @Before
    public void setUp() {
        cardService = Mockito.mock(CardService.class);
        mapper = new ReportCardMapper(cardService);

        when(cardService.getStandardReportCardInputSubjectTypes(Mockito.any())).thenReturn(Collections.emptyList());
        when(cardService.getStandardReportCardInputPrograms(Mockito.any())).thenReturn(Collections.emptyList());
        when(cardService.getStandardReportCardInputEncounterTypes(Mockito.any())).thenReturn(Collections.emptyList());
    }

    @Test
    public void toBundleIncludesInlineCustomCardConfig() {
        CustomCardConfig config = new CustomCardConfig();
        config.setUuid("config-uuid-1");
        config.setName("My Custom Card");
        config.setDataRule("some rule");
        config.setHtmlFileS3Key("config-uuid-1.html");

        ReportCard card = new ReportCard();
        card.setUuid("card-uuid-1");
        card.setName("Card");
        card.setCustomCardConfig(config);

        ReportCardBundleContract result = mapper.toBundle(card);

        assertNotNull(result.getCustomCardConfig());
        CustomCardConfigRequest inlined = result.getCustomCardConfig();
        assertEquals("config-uuid-1", inlined.getUuid());
        assertEquals("My Custom Card", inlined.getName());
        assertEquals("some rule", inlined.getDataRule());
        assertEquals("config-uuid-1.html", inlined.getHtmlFileS3Key());
    }

    @Test
    public void toBundleProducesNullCustomCardConfigWhenAbsent() {
        ReportCard card = new ReportCard();
        card.setUuid("card-uuid-2");
        card.setName("Card");

        ReportCardBundleContract result = mapper.toBundle(card);

        assertNull(result.getCustomCardConfig());
    }
}

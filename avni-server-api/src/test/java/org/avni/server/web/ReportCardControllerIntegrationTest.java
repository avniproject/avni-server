package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A report card whose action is Open dashboard stores its target dashboard in actionDetail
 * (avniproject/avni-server#1101). The phone gets the card from the card sync, so the target has to come out of the
 * sync the way the phone reads it.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ReportCardControllerIntegrationTest extends AbstractControllerIntegrationTest {
    @Autowired
    private DashboardRepository dashboardRepository;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void cardSyncCarriesOpenDashboardTargetInActionDetail() throws Exception {
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(UUID.randomUUID().toString());
        dashboard.setName("Monthly overview");
        dashboardRepository.save(dashboard);

        // What the web app sends when it saves a card: a query card, with the target dashboard in actionDetailDashboardUUID.
        String cardJson = mapper.writeValueAsString(Map.of(
                "name", "Open the monthly overview",
                "color", "#1b6ec2",
                "count", 1,
                "query", "({params}) => []",
                "action", "OpenDashboard",
                "actionDetailDashboardUUID", dashboard.getUuid()));
        // Binding the request and writing the response both go through Spring's own JSON mapper, so this checks the field name.
        // Qualified because the base class has its own post(path, json), which hides the static import.
        MvcResult saved = mockMvc.perform(MockMvcRequestBuilders.post("/web/reportCard").contentType(MediaType.APPLICATION_JSON).content(cardJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actionDetailDashboardUUID").value(dashboard.getUuid()))
                .andReturn();
        String cardUuid = mapper.readTree(saved.getResponse().getContentAsString()).path("uuid").textValue();

        JsonNode syncedCard = cardFromTheCardSync(cardUuid);

        assertThat(syncedCard.path("action").textValue()).as("action of the card in the card sync").isEqualTo("OpenDashboard");
        assertThat(syncedCard.path("actionDetail").path("dashboardUUID").textValue())
                .as("the target dashboard the phone reads from actionDetail")
                .isEqualTo(dashboard.getUuid());
    }

    // The card sync as the phone calls it, from the beginning of time. The phone reads the cards from _embedded.card.
    private JsonNode cardFromTheCardSync(String cardUuid) throws Exception {
        String body = mockMvc.perform(get("/v2/card/search/lastModified")
                        .param("lastModifiedDateTime", "2000-01-01T00:00:00.000Z")
                        .param("now", DateTime.now(DateTimeZone.UTC).plusMinutes(1).toString())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode card : mapper.readTree(body).path("_embedded").path("card")) {
            if (cardUuid.equals(card.path("uuid").textValue())) {
                return card;
            }
        }
        throw new AssertionError("the saved card " + cardUuid + " is not under _embedded.card in the card sync: " + body);
    }
}

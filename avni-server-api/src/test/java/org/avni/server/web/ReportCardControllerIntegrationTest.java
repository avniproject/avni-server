package org.avni.server.web;

import com.fasterxml.jackson.databind.JsonNode;
import org.assertj.core.api.SoftAssertions;
import org.avni.server.common.AbstractControllerIntegrationTest;
import org.avni.server.dao.CardRepository;
import org.avni.server.dao.CustomCardConfigRepository;
import org.avni.server.dao.DashboardRepository;
import org.avni.server.domain.Dashboard;
import org.avni.server.domain.ReportCard;
import org.avni.server.domain.ReportCardAction;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.mapper.dashboard.DefaultDashboardConstants;
import org.avni.server.service.CardService;
import org.avni.server.service.DashboardService;
import org.avni.server.service.OrganisationService;
import org.avni.server.web.contract.reports.DashboardBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionBundleContract;
import org.avni.server.web.contract.reports.DashboardSectionCardMappingBundleContract;
import org.avni.server.web.request.reports.ReportCardBundleRequest;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A report card whose action is Open dashboard stores its target dashboard in actionDetail
 * (avniproject/avni-server#1101). The phone gets the card from the card sync, so the target has to come out of the
 * sync the way the phone reads it. The web app reads it back from the card endpoints, a refused save has to leave
 * nothing behind, and a bundle carries the target to another organisation.
 */
@Sql(value = {"/tear-down.sql", "/test-data.sql"}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(value = {"/tear-down.sql"}, executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
public class ReportCardControllerIntegrationTest extends AbstractControllerIntegrationTest {
    private static final String CARD_NAME = "Open the monthly overview";
    // OpenCHS, the parent of demo and of a-demo in test-data.sql.
    private static final Long PARENT_ORGANISATION_ID = 1L;
    private static final String DEFAULT_DASHBOARD_UUID = DefaultDashboardConstants.DASHBOARD_NAME_UUID_MAPPING.get(DefaultDashboardConstants.DEFAULT_DASHBOARD);

    @Autowired
    private DashboardRepository dashboardRepository;
    @Autowired
    private CardRepository cardRepository;
    @Autowired
    private CustomCardConfigRepository customCardConfigRepository;
    @Autowired
    private CardService cardService;
    @Autowired
    private DashboardService dashboardService;
    @Autowired
    private OrganisationService organisationService;
    @Autowired
    private DataSource dataSource;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        setUser("demo-admin");
    }

    @Test
    public void cardSyncCarriesOpenDashboardTargetInActionDetail() throws Exception {
        Dashboard dashboard = saveDashboard();
        String cardJson = openDashboardCardJson(dashboard);
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

    // Saving a card still needs the "Edit offline dashboard and report card" privilege (avniproject/avni-server#1101).
    // The target dashboard exists, so the privilege is the only thing that can refuse the save.
    @Test
    public void newCardIsRefusedWithoutTheEditDashboardPrivilege() throws Exception {
        String cardJson = openDashboardCardJson(saveDashboard());

        setUser("user-no-access");
        mockMvc.perform(MockMvcRequestBuilders.post("/web/reportCard").contentType(MediaType.APPLICATION_JSON).content(cardJson))
                .andExpect(status().isForbidden());

        setUser("demo-admin");
        String savedCards = mockMvc.perform(get("/web/reportCard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(savedCards).findValuesAsText("name")).as("names of the saved cards").doesNotContain(CARD_NAME);
    }

    // The save answers from the card it has just built, so only a second request shows what was stored.
    @Test
    public void cardReadsBackWithItsTargetFromAFreshLoad() throws Exception {
        Dashboard dashboard = saveDashboard();
        long cardId = postCard(openDashboardCardJson(dashboard)).path("id").asLong();

        mockMvc.perform(get("/web/reportCard/" + cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("OpenDashboard"))
                .andExpect(jsonPath("$.actionDetailDashboardUUID").value(dashboard.getUuid()));
    }

    // A card switched away from Open dashboard arrives with its old target still in the request. The web app sends every
    // action field it has, whatever the action is, and avniproject/avni-webapp#1830 adds actionDetailDashboardUUID to those fields.
    @Test
    public void switchingToViewSubjectProfileClearsTheTargetOnReadBackAndSync() throws Exception {
        Dashboard dashboard = saveDashboard();
        JsonNode saved = postCard(openDashboardCardJson(dashboard));
        long cardId = saved.path("id").asLong();

        mockMvc.perform(put("/web/reportCard/" + cardId).contentType(MediaType.APPLICATION_JSON)
                        .content(queryCardJson(CARD_NAME, "ViewSubjectProfile", dashboard.getUuid())))
                .andExpect(status().isOk());

        JsonNode readBack = mapper.readTree(mockMvc.perform(get("/web/reportCard/" + cardId))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        JsonNode syncedCard = cardFromTheCardSync(saved.path("uuid").textValue());

        // Soft, so one failure names every place that still shows the target: the web read-back and the
        // card sync each read the stored detail their own way.
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(readBack.path("action").textValue()).as("action read back").isEqualTo("ViewSubjectProfile");
            softly.assertThat(readBack.path("actionDetailDashboardUUID").textValue()).as("target read back").isNull();
            softly.assertThat(syncedCard.path("action").textValue()).as("action in the card sync").isEqualTo("ViewSubjectProfile");
            softly.assertThat(syncedCard.path("actionDetail").path("dashboardUUID").textValue()).as("target in the card sync").isNull();
        });
    }

    // Editing a card needs the same privilege as saving a new one (avniproject/avni-server#1101).
    @Test
    public void editIsRefusedWithoutTheEditDashboardPrivilege() throws Exception {
        Dashboard dashboard = saveDashboard();
        long cardId = postCard(openDashboardCardJson(dashboard)).path("id").asLong();

        setUser("user-no-access");
        mockMvc.perform(put("/web/reportCard/" + cardId).contentType(MediaType.APPLICATION_JSON)
                        .content(queryCardJson("Renamed by someone without the privilege", "OpenDashboard", dashboard.getUuid())))
                .andExpect(status().isForbidden());

        setUser("demo-admin");
        mockMvc.perform(get("/web/reportCard/" + cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(CARD_NAME));
    }

    // The inline custom card config is saved before the card is checked, so only the controller's transaction takes it
    // back when the card is refused.
    @Test
    public void refusedSaveLeavesNoInlineCustomCardConfigBehind() throws Exception {
        String missingDashboardUuid = UUID.randomUUID().toString();
        String configName = "Config of a refused card " + UUID.randomUUID();
        String cardJson = mapper.writeValueAsString(Map.of(
                "name", CARD_NAME,
                "color", "#1b6ec2",
                "count", 1,
                "action", "OpenDashboard",
                "actionDetailDashboardUUID", missingDashboardUuid,
                "customCardConfig", Map.of("name", configName)));

        try {
            assertSaveRefusedAsMissingDashboard(cardJson, missingDashboardUuid);

            assertThat(customCardConfigRepository.findByNameIgnoreCaseAndIsVoidedFalse(configName))
                    .as("the custom card config the refused request carried inline").isNull();
            assertThat(savedCardNames()).as("names of the saved cards").doesNotContain(CARD_NAME);
        } finally {
            removeCustomCardConfig(configName);
        }
    }

    // Org 2 and org 3 are siblings under org 1, so a dashboard saved in org 3 is not visible from org 2.
    @Test
    public void webSaveRefusesADashboardFromAnotherOrganisation() throws Exception {
        switchToUserOfAnotherOrganisation("a-demo-admin");
        Dashboard dashboardOfAnotherOrganisation = saveDashboard();
        switchToUserOfAnotherOrganisation("demo-admin");

        assertSaveRefusedAsMissingDashboard(openDashboardCardJson(dashboardOfAnotherOrganisation), dashboardOfAnotherOrganisation.getUuid());

        assertThat(savedCardNames()).as("names of the saved cards").doesNotContain(CARD_NAME);
    }

    // A bundle imports its cards before its dashboards, so a card can name a dashboard that is not there yet.
    @Test
    public void bundleOrderCardsBeforeDashboardsKeepsTheTarget() throws Exception {
        String dashboardUuid = UUID.randomUUID().toString();
        ReportCardBundleRequest card = new ReportCardBundleRequest();
        card.setUuid(UUID.randomUUID().toString());
        card.setName(CARD_NAME);
        card.setColor("#1b6ec2");
        card.setCount(1);
        card.setQuery("({params}) => []");
        card.setAction("OpenDashboard");
        card.setActionDetailDashboardUUID(dashboardUuid);

        cardService.saveCards(new ReportCardBundleRequest[]{card});

        assertThat(dashboardRepository.findByUuid(dashboardUuid)).as("the dashboard before its file is imported").isNull();
        assertThat(cardRepository.findByUuid(card.getUuid()).getActionDetailDashboardUUID())
                .as("target stored on the card imported first").isEqualTo(dashboardUuid);

        dashboardService.saveDashboards(new DashboardBundleContract[]{dashboardWithOneCard(dashboardUuid, card.getUuid())});

        Dashboard dashboard = dashboardRepository.findByUuid(dashboardUuid);
        assertThat(dashboard).as("the dashboard after its file is imported").isNotNull();
        mockMvc.perform(get("/web/dashboard/" + dashboard.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sections[0].dashboardSectionCardMappings[0].card.uuid").value(card.getUuid()))
                .andExpect(jsonPath("$.sections[0].dashboardSectionCardMappings[0].card.actionDetailDashboardUUID").value(dashboardUuid));
    }

    // The bundle is how a configuration moves to another organisation, so the target has to survive the trip out and back in.
    @Test
    public void bundleExportCarriesTheTargetAndAnotherOrganisationRestoresIt() throws Exception {
        Dashboard dashboard = saveDashboard();
        String cardUuid = postCard(openDashboardCardJson(dashboard)).path("uuid").textValue();

        String reportCardJson = reportCardJsonOfTheBundleExport();

        JsonNode exportedCard = cardWithUuid(mapper.readTree(reportCardJson), cardUuid);
        assertThat(exportedCard.path("action").textValue()).as("action in the exported reportCard.json").isEqualTo("OpenDashboard");
        assertThat(exportedCard.path("actionDetailDashboardUUID").textValue())
                .as("target in the exported reportCard.json").isEqualTo(dashboard.getUuid());

        switchToUserOfAnotherOrganisation("a-demo-admin");
        cardService.saveCards(mapper.readValue(reportCardJson, ReportCardBundleRequest[].class));

        ReportCard restoredCard = cardRepository.findByUuid(cardUuid);
        assertThat(restoredCard).as("the card in the other organisation").isNotNull();
        assertThat(restoredCard.getOrganisationId()).as("organisation of the restored card")
                .isEqualTo(UserContextHolder.getUserContext().getOrganisationId());
        assertThat(restoredCard.getAction()).as("action of the restored card").isEqualTo(ReportCardAction.OpenDashboard);
        assertThat(restoredCard.getActionDetailDashboardUUID()).as("target of the restored card").isEqualTo(dashboard.getUuid());
    }

    // A child organisation sees its parent's dashboards as well as its own, and the Default Dashboard has the same uuid in
    // every organisation. So the uuid can match two rows, and a lookup that expects one row answered 500.
    @Test
    public void childOrganisationSavesACardThatOpensTheDefaultDashboard() throws Exception {
        Long thisOrganisationId = UserContextHolder.getUserContext().getOrganisationId();
        saveDashboardInTheParentOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", false);
        saveDashboardInThisOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", false);
        assertThat(organisationsHoldingDashboardAsSeenHere(DEFAULT_DASHBOARD_UUID))
                .containsExactlyInAnyOrder(PARENT_ORGANISATION_ID, thisOrganisationId);

        long cardId = postCard(queryCardJson(CARD_NAME, "OpenDashboard", DEFAULT_DASHBOARD_UUID)).path("id").asLong();

        assertReadsBackOpeningDashboard(cardId, DEFAULT_DASHBOARD_UUID);
    }

    // Deleted in both organisations, so no live copy is left and the target is refused as missing.
    @Test
    public void childOrganisationRefusesATargetDeletedInItAndInItsParent() throws Exception {
        Long thisOrganisationId = UserContextHolder.getUserContext().getOrganisationId();
        saveDashboardInTheParentOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", true);
        saveDashboardInThisOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", true);
        assertThat(organisationsHoldingDashboardAsSeenHere(DEFAULT_DASHBOARD_UUID))
                .containsExactlyInAnyOrder(PARENT_ORGANISATION_ID, thisOrganisationId);

        assertSaveRefusedAsMissingDashboard(queryCardJson(CARD_NAME, "OpenDashboard", DEFAULT_DASHBOARD_UUID), DEFAULT_DASHBOARD_UUID);
        assertThat(savedCardNames()).doesNotContain(CARD_NAME);
    }

    // The editor lists the parent's live copy, so the user can pick it, and the save has to accept it.
    @Test
    public void childOrganisationSavesATargetItDeletedWhileItsParentKeepsIt() throws Exception {
        Long thisOrganisationId = UserContextHolder.getUserContext().getOrganisationId();
        saveDashboardInTheParentOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", false);
        saveDashboardInThisOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", true);
        assertThat(organisationsHoldingDashboardAsSeenHere(DEFAULT_DASHBOARD_UUID))
                .containsExactlyInAnyOrder(PARENT_ORGANISATION_ID, thisOrganisationId);
        String listed = mockMvc.perform(get("/web/dashboard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(mapper.readTree(listed).findValuesAsText("uuid")).as("dashboards the editor offers")
                .filteredOn(DEFAULT_DASHBOARD_UUID::equals).hasSize(1);

        long cardId = postCard(queryCardJson(CARD_NAME, "OpenDashboard", DEFAULT_DASHBOARD_UUID)).path("id").asLong();

        assertReadsBackOpeningDashboard(cardId, DEFAULT_DASHBOARD_UUID);
    }

    // The edit runs the same check as the save, so it has to accept a target that matches two rows too.
    @Test
    public void childOrganisationEditsACardToOpenTheDefaultDashboard() throws Exception {
        Long thisOrganisationId = UserContextHolder.getUserContext().getOrganisationId();
        saveDashboardInTheParentOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", false);
        saveDashboardInThisOrganisation(DEFAULT_DASHBOARD_UUID, "Default Dashboard", false);
        assertThat(organisationsHoldingDashboardAsSeenHere(DEFAULT_DASHBOARD_UUID))
                .containsExactlyInAnyOrder(PARENT_ORGANISATION_ID, thisOrganisationId);
        long cardId = postCard(openDashboardCardJson(saveDashboard())).path("id").asLong();

        mockMvc.perform(put("/web/reportCard/" + cardId).contentType(MediaType.APPLICATION_JSON)
                        .content(queryCardJson(CARD_NAME, "OpenDashboard", DEFAULT_DASHBOARD_UUID)))
                .andExpect(status().isOk());

        assertReadsBackOpeningDashboard(cardId, DEFAULT_DASHBOARD_UUID);
    }

    // What worked before the fix: one row, held by the parent only. This guards it.
    @Test
    public void childOrganisationSavesACardThatOpensADashboardOnlyItsParentHolds() throws Exception {
        String uuid = UUID.randomUUID().toString();
        saveDashboardInTheParentOrganisation(uuid, "Programme overview", false);
        assertThat(organisationsHoldingDashboardAsSeenHere(uuid)).containsExactly(PARENT_ORGANISATION_ID);

        long cardId = postCard(queryCardJson(CARD_NAME, "OpenDashboard", uuid)).path("id").asLong();

        assertReadsBackOpeningDashboard(cardId, uuid);
    }

    // A user can be looked up only from its own organisation, so the next user is looked up with no organisation's role in force.
    private void switchToUserOfAnotherOrganisation(String username) {
        UserContextHolder.clear();
        setUser(username);
    }

    // The test database is cleared between tests, except custom card configs. One that outlived its test would stop the
    // next clear-down at DELETE FROM users, which it references, and fail every test that runs after it.
    private void removeCustomCardConfig(String configName) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from public.report_card where custom_card_config_id in (select id from public.custom_card_config where name = ?)", configName);
        jdbcTemplate.update("delete from public.custom_card_config where name = ?", configName);
    }

    private Dashboard saveDashboard() {
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(UUID.randomUUID().toString());
        dashboard.setName("Monthly overview");
        dashboardRepository.save(dashboard);
        return dashboard;
    }

    // Saves the dashboard as the parent organisation. The current user is put back afterwards. No user belongs to the parent
    // organisation: admin has no organisation. A connection for a user of this organisation is refused a row of another
    // organisation by row-level security. So this is a plain insert with the default super admin in force, as the #1102 tests do.
    private void saveDashboardInTheParentOrganisation(String uuid, String name, boolean voided) {
        String username = UserContextHolder.getUserContext().getUserName();
        switchToUserOfAnotherOrganisation("admin");
        try {
            new JdbcTemplate(dataSource).update("insert into dashboard (uuid, name, organisation_id, is_voided, version, created_by_id, last_modified_by_id, created_date_time, last_modified_date_time) " +
                    "values (?, ?, ?, ?, 0, 1, 1, now(), now())", uuid, name, PARENT_ORGANISATION_ID, voided);
        } finally {
            switchToUserOfAnotherOrganisation(username);
        }
    }

    private void saveDashboardInThisOrganisation(String uuid, String name, boolean voided) {
        Dashboard dashboard = new Dashboard();
        dashboard.setUuid(uuid);
        dashboard.setName(name);
        dashboard.setVoided(voided);
        dashboardRepository.save(dashboard);
    }

    // The organisations that own the dashboards with this uuid which the current user can see.
    private List<Long> organisationsHoldingDashboardAsSeenHere(String uuid) {
        return dashboardRepository.findAllByUuidIn(List.of(uuid)).stream()
                .map(Dashboard::getOrganisationId)
                .toList();
    }

    private void assertReadsBackOpeningDashboard(long cardId, String dashboardUuid) throws Exception {
        mockMvc.perform(get("/web/reportCard/" + cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("OpenDashboard"))
                .andExpect(jsonPath("$.actionDetailDashboardUUID").value(dashboardUuid));
    }

    private String openDashboardCardJson(Dashboard dashboard) throws Exception {
        return queryCardJson(CARD_NAME, "OpenDashboard", dashboard.getUuid());
    }

    private String queryCardJson(String name, String action, String dashboardUuid) throws Exception {
        // A query card with the fields the web app sends (name, color, count, query, action), plus
        // actionDetailDashboardUUID, the name avniproject/avni-webapp#1830 specifies for the target dashboard.
        return mapper.writeValueAsString(Map.of(
                "name", name,
                "color", "#1b6ec2",
                "count", 1,
                "query", "({params}) => []",
                "action", action,
                "actionDetailDashboardUUID", dashboardUuid));
    }

    // Saves a card as the current user, which has to succeed, and returns the card the server answered with.
    private JsonNode postCard(String cardJson) throws Exception {
        String saved = mockMvc.perform(MockMvcRequestBuilders.post("/web/reportCard").contentType(MediaType.APPLICATION_JSON).content(cardJson))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(saved);
    }

    // A save naming a dashboard the user cannot see is refused with 400, and the reason is in the body.
    private void assertSaveRefusedAsMissingDashboard(String cardJson, String dashboardUuid) throws Exception {
        String body = mockMvc.perform(MockMvcRequestBuilders.post("/web/reportCard").contentType(MediaType.APPLICATION_JSON).content(cardJson))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Dashboard with uuid " + dashboardUuid + " doesn't exist");
    }

    private List<String> savedCardNames() throws Exception {
        String savedCards = mockMvc.perform(get("/web/reportCard"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return mapper.readTree(savedCards).findValuesAsText("name");
    }

    // The reportDashboard.json entry of a bundle: one dashboard with one section holding one card.
    private DashboardBundleContract dashboardWithOneCard(String dashboardUuid, String cardUuid) {
        DashboardSectionCardMappingBundleContract mapping = new DashboardSectionCardMappingBundleContract();
        mapping.setUuid(UUID.randomUUID().toString());
        mapping.setReportCardUUID(cardUuid);
        mapping.setDisplayOrder(1.0);

        DashboardSectionBundleContract section = new DashboardSectionBundleContract();
        section.setUuid(UUID.randomUUID().toString());
        section.setName("Overview");
        section.setDescription("The cards of the monthly overview");
        section.setViewType("Tile");
        section.setDisplayOrder(1.0);
        section.setDashboardSectionCardMappings(List.of(mapping));

        DashboardBundleContract dashboard = new DashboardBundleContract();
        dashboard.setUuid(dashboardUuid);
        dashboard.setName("Monthly overview");
        dashboard.setSections(List.of(section));
        return dashboard;
    }

    // The cards of the current organisation as a bundle download writes them, read back out of the zip.
    private String reportCardJsonOfTheBundleExport() throws Exception {
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(zip)) {
            organisationService.addReportCards(zos);
        }
        try (ZipInputStream entries = new ZipInputStream(new ByteArrayInputStream(zip.toByteArray()))) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                if (entry.getName().equals("reportCard.json")) {
                    return new String(entries.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError("reportCard.json is not in the bundle export");
    }

    private JsonNode cardWithUuid(JsonNode cards, String cardUuid) {
        for (JsonNode card : cards) {
            if (cardUuid.equals(card.path("uuid").textValue())) {
                return card;
            }
        }
        throw new AssertionError("the card " + cardUuid + " is not among the exported cards: " + cards);
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

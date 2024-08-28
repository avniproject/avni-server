package org.avni.server.mapper.dashboard;

import java.util.HashMap;
import java.util.Map;

public interface DefaultDashboardConstants {
    String SCHEDULED_VISITS_CARD = "Scheduled visits";
    String OVERDUE_VISITS_CARD = "Overdue visits";
    String TOTAL_CARD = "Total";
    String RECENT_REGISTRATIONS_CARD = "Recent registrations";
    String RECENT_ENROLMENTS_CARD = "Recent enrolments";
    String RECENT_VISITS_CARD = "Recent visits";
    String DUE_CHECKLIST_CARD = "Due checklist";
    Map<String, String> CARD_NAME_UUID_MAPPING = new HashMap<String, String>() {{
        put(SCHEDULED_VISITS_CARD, "6085c2f4-52e7-4b08-85b6-d6b2612b4cf5");
        put(OVERDUE_VISITS_CARD, "85ce7239-e8b5-4e57-b07d-66c18cee47b2");
        put(TOTAL_CARD, "a1673f8a-c394-4bcf-8b6f-63d83a5443e2");
        put(RECENT_REGISTRATIONS_CARD, "f366f35a-5c4f-4ff7-b510-2dc9f5f88847");
        put(RECENT_ENROLMENTS_CARD, "e1036b69-df46-4351-9916-10cd4cfcb6bd");
        put(RECENT_VISITS_CARD, "dd961ee1-9d4e-4ec9-99f0-99b36672be7c");
        put(DUE_CHECKLIST_CARD, "9b7632dd-4e98-429a-8e42-67a947bf9ece");
    }};

    String DEFAULT_DASHBOARD = "Default Dashboard";
    Map<String, String> DASHBOARD_NAME_UUID_MAPPING = new HashMap<String, String>() {{
        put(DEFAULT_DASHBOARD, "c4d3bc0a-027e-4a6a-87dd-85e5b7285523");
    }};

    String VISIT_DETAILS_SECTION = "Visit Details";
    String RECENT_STATISTICS_SECTION = "Recent Statistics";
    String REGISTRATION_OVERVIEW_SECTION = "Registration Overview";
    Map<String, String> SECTION_NAME_UUID_MAPPING = new HashMap<String, String>() {{
        put(VISIT_DETAILS_SECTION, "741711ef-df17-4884-8928-20dee701479e");
        put(RECENT_STATISTICS_SECTION, "fb302038-25a1-4cd6-9f56-80ef67b21103");
        put(REGISTRATION_OVERVIEW_SECTION, "2ce712c2-3fa3-4ca4-9703-95766ef512c2");
    }};

    Map<String, String> SECTION_CARD_MAPPING = new HashMap<String, String>() {{
        put(VISIT_DETAILS_SECTION.concat(SCHEDULED_VISITS_CARD), "4d139c45-1854-46ec-ad80-40725f7b9b8a");
        put(VISIT_DETAILS_SECTION.concat(OVERDUE_VISITS_CARD), "4dff9285-88b3-43de-a90b-9823ab32e433");
        put(RECENT_STATISTICS_SECTION.concat(RECENT_REGISTRATIONS_CARD), "8afecd6c-741c-4871-86f1-bce171f8bfd8");
        put(RECENT_STATISTICS_SECTION.concat(RECENT_ENROLMENTS_CARD), "383b8df8-93d8-43d9-bc96-545e1176fe63");
        put(RECENT_STATISTICS_SECTION.concat(RECENT_VISITS_CARD), "9fb198b6-ae10-4c3f-a8e6-652b7d1b7e9c");
        put(REGISTRATION_OVERVIEW_SECTION.concat(TOTAL_CARD), "e02a68ed-c02b-4cba-a5b5-6a5c71ab5eb8");
    }};

    String SUBJECT_TYPE_FILTER = "Subject Type";
    String AS_ON_DATE_FILTER = "As On Date";
    Map<String, String> FILTER_NAME_UUID_MAPPING = new HashMap<String, String>() {{
        put(SUBJECT_TYPE_FILTER, "20367018-a168-43ff-a28f-64cd46ad3e2c");
        put(AS_ON_DATE_FILTER, "efea5b8d-d621-47d0-a02c-c9b1a667b680");
    }};

    String WHITE_BG_COLOUR = "#ffffff";
    String RED_BG_COLOUR = "#d32f2f";
    String GREEN_BG_COLOUR = "#388e3c";

}

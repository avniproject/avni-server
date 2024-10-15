package org.avni.server.mapper.dashboard;

import org.avni.server.domain.StandardReportCardTypeType;

import java.util.HashMap;
import java.util.Map;

import static org.avni.server.domain.StandardReportCardTypeType.*;

public interface DefaultDashboardConstants {
    Map<StandardReportCardTypeType, String> CARD_TYPE_UUID_MAPPING = new HashMap<StandardReportCardTypeType, String>() {{
        put(ScheduledVisits, "6085c2f4-52e7-4b08-85b6-d6b2612b4cf5");
        put(OverdueVisits, "85ce7239-e8b5-4e57-b07d-66c18cee47b2");
        put(Total, "a1673f8a-c394-4bcf-8b6f-63d83a5443e2");
        put(RecentRegistrations, "f366f35a-5c4f-4ff7-b510-2dc9f5f88847");
        put(RecentEnrolments, "e1036b69-df46-4351-9916-10cd4cfcb6bd");
        put(RecentVisits, "dd961ee1-9d4e-4ec9-99f0-99b36672be7c");
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
        put(VISIT_DETAILS_SECTION.concat(ScheduledVisits.name()), "4d139c45-1854-46ec-ad80-40725f7b9b8a");
        put(VISIT_DETAILS_SECTION.concat(OverdueVisits.name()), "4dff9285-88b3-43de-a90b-9823ab32e433");
        put(RECENT_STATISTICS_SECTION.concat(RecentRegistrations.name()), "8afecd6c-741c-4871-86f1-bce171f8bfd8");
        put(RECENT_STATISTICS_SECTION.concat(RecentEnrolments.name()), "383b8df8-93d8-43d9-bc96-545e1176fe63");
        put(RECENT_STATISTICS_SECTION.concat(RecentVisits.name()), "9fb198b6-ae10-4c3f-a8e6-652b7d1b7e9c");
        put(REGISTRATION_OVERVIEW_SECTION.concat(Total.name()), "e02a68ed-c02b-4cba-a5b5-6a5c71ab5eb8");
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

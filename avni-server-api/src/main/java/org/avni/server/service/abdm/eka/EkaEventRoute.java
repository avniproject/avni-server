package org.avni.server.service.abdm.eka;

import com.fasterxml.jackson.databind.JsonNode;
import org.avni.server.service.abdm.AbdmJson;
import org.avni.server.service.abdm.hip.AbdmFlows;

import java.util.Locale;
import java.util.Set;

// Which handler an Eka webhook event goes to, from the envelope's "event" field (spec section 5).
public enum EkaEventRoute {
    LINK_CARE_CONTEXT, HIP_DATA_FETCH, CONSENT_UPDATE, HIU_DATA_PUSH, DISCOVER, DISCOVER_LINK_INIT, DISCOVER_LINK_CONFIRM, NONE;

    // Event names squashed to letters and digits, without the "abha"/"abdm" prefix: the spec's names and close variants.
    private static final Set<String> LINK_CARE_CONTEXT_NAMES = Set.of("linkcarecontext", "linkcarecontexts", "carecontextlink",
            "carecontextlinked", "carecontextlinkstatus", "linkcarecontextstatus");
    private static final Set<String> HIP_DATA_FETCH_NAMES = Set.of("hipdatafetch", "datafetch", "hipdatarequest", "hiphealthinformationrequest");
    private static final Set<String> CONSENT_UPDATE_NAMES = Set.of("consentupdate", "consentupdated", "consentstatus", "consentstatusupdate",
            "consentnotify");
    private static final Set<String> HIU_DATA_PUSH_NAMES = Set.of("hiudatapush", "datapush", "hiudatatransfer", "hiuhealthinformationtransfer");
    // Patient-led linking (spec 2.7): Eka's three names, as published.
    private static final Set<String> DISCOVER_NAMES = Set.of("carecontextdiscover");
    private static final Set<String> DISCOVER_LINK_INIT_NAMES = Set.of("carecontextdiscoverlinkinit");
    private static final Set<String> DISCOVER_LINK_CONFIRM_NAMES = Set.of("contextdiscoverlinkconfirm", "carecontextdiscoverlinkconfirm");

    public static EkaEventRoute of(String name) {
        String key = name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        key = key.replaceFirst("^(abha|abdm)", "");
        if (LINK_CARE_CONTEXT_NAMES.contains(key)) return LINK_CARE_CONTEXT;
        if (HIP_DATA_FETCH_NAMES.contains(key)) return HIP_DATA_FETCH;
        if (CONSENT_UPDATE_NAMES.contains(key)) return CONSENT_UPDATE;
        if (HIU_DATA_PUSH_NAMES.contains(key)) return HIU_DATA_PUSH;
        if (DISCOVER_NAMES.contains(key)) return DISCOVER;
        if (DISCOVER_LINK_INIT_NAMES.contains(key)) return DISCOVER_LINK_INIT;
        if (DISCOVER_LINK_CONFIRM_NAMES.contains(key)) return DISCOVER_LINK_CONFIRM;
        return NONE;
    }

    // The ABDM events carry "event"; "event_type" is what Eka's non-ABDM events use and is not read.
    public static String eventName(JsonNode event) {
        String name = AbdmFlows.firstNonNull(AbdmJson.text(event, "event"), AbdmJson.text(event, "event_name"));
        return name == null ? "unknown" : name.trim();
    }
}

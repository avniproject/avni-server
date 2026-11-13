package org.avni.server.service.abdm;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

// The Eka webhook inbox (abdm_inbound_event, V1_414 and V1_417). EkaWebhookController stores each verified event here
// before Eka is answered 200; EkaEventWorker takes due events, handles each as its organisation and marks it done, or
// puts it back for a later try after a growing wait, up to MAX_ATTEMPTS, after which it is FAILED. A consent's withdrawal,
// refusal or expiry is never FAILED: past the cap it keeps its body and is tried every PAST_CAP_WAIT (retryUntilHandled).
// One row per event id per organisation, so an event Eka delivers again has one effect. Nothing is held in memory: a
// restart loses nothing, and a row left in progress by a stopped server is swept back. The worker takes one event at a
// time, and an outcome is written only by the claim that still holds the event.
// A consent's updates are taken in the order they came, and an event of a consent (a data push) waits behind any update
// of that consent that came before it, so a push finds its grant. A push holds nothing back: it may come before its grant.
// A withdrawal naming only artefacts is stored under the consent of the waiting update that names them (pendingConsentKeyOf).
// One worker serves every organisation, so its statements run as no organisation and name organisation_id themselves.
@Component
public class AbdmInbox {
    public static final String RECEIVED = "RECEIVED";
    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String DONE = "DONE";
    public static final String FAILED = "FAILED";
    public static final int MAX_ATTEMPTS = 10;
    // 15 s, doubling to at most 30 minutes: an hour and a half from the first try to the last.
    static final Duration FIRST_WAIT = Duration.ofSeconds(15);
    public static final Duration MAX_WAIT = Duration.ofMinutes(30);
    // A patient's withdrawal past the cap: slow enough not to flood the alarm, soon enough to apply within the hour of a fix.
    public static final Duration PAST_CAP_WAIT = Duration.ofHours(1);
    // Longer than any handler takes (Eka calls time out within a minute), so only an abandoned row is swept.
    public static final Duration IN_PROGRESS_TIMEOUT = Duration.ofMinutes(15);
    private static final int MAX_ERROR = 1000;
    private static final String HOLDING_ROUTE = "CONSENT_UPDATE";

    // claimedAt: when the claim that holds it took it, which fences every outcome written for it.
    public record Event(long id, long organisationId, String source, String eventId, String eventName, String route, String body,
                        int attempts, Instant claimedAt) {
    }

    public record Sweep(int returned, int failed) {
    }

    private static final RowMapper<Event> EVENT = (rs, n) -> new Event(rs.getLong("id"), rs.getLong("organisation_id"),
            rs.getString("source"), rs.getString("event_id"), rs.getString("event_name"), rs.getString("route"), rs.getString("body"),
            rs.getInt("attempts"), rs.getTimestamp("claimed_at") == null ? null : rs.getTimestamp("claimed_at").toInstant());

    // The due events are picked once, in a materialised CTE: a sub-select in the update's where clause can be run again
    // for each row the update visits, and each run, skipping the rows already taken, would hand out one more.
    private static final String CLAIM = "with picked as materialized (select c.id from abdm_inbound_event c " +
            "  where c.status = 'RECEIVED' and c.next_attempt_at <= :now " +
            "  and not exists (select 1 from abdm_inbound_event p where p.organisation_id = c.organisation_id " +
            "    and p.consent_key = c.consent_key and p.id < c.id and p.route = '" + HOLDING_ROUTE + "' " +
            "    and p.status in ('RECEIVED', 'IN_PROGRESS')) " +
            "  order by c.id limit :max for update skip locked) " +
            "update abdm_inbound_event e set status = 'IN_PROGRESS', attempts = e.attempts + 1, claimed_at = :now, updated_at = :now " +
            "from picked where e.id = picked.id " +
            "returning e.id, e.organisation_id, e.source, e.event_id, e.event_name, e.route, e.body, e.attempts, e.claimed_at";

    private final NamedParameterJdbcTemplate jdbc;
    private final AbdmOrgContext orgContext;

    public AbdmInbox(NamedParameterJdbcTemplate jdbc, AbdmOrgContext orgContext) {
        this.jdbc = jdbc;
        this.orgContext = orgContext;
    }

    // False when the organisation already has this event id: a redelivery, stored once.
    public boolean receive(long organisationId, String source, String eventId, String eventName, String route, String consentKey,
                           String body, Instant now) {
        int inserted = jdbc.update("insert into abdm_inbound_event (organisation_id, source, event_id, event_name, route, consent_key, body, " +
                        "status, attempts, next_attempt_at, received_at, updated_at) values (:org, :source, :eventId, :name, :route, :consentKey, " +
                        ":body, 'RECEIVED', 0, :now, :now, :now) on conflict (organisation_id, source, event_id) do nothing",
                new MapSqlParameterSource("org", organisationId).addValue("source", source, Types.VARCHAR)
                        .addValue("eventId", eventId, Types.VARCHAR).addValue("name", eventName, Types.VARCHAR)
                        .addValue("route", route, Types.VARCHAR).addValue("consentKey", consentKey, Types.VARCHAR)
                        .addValue("body", body, Types.VARCHAR).addValue("now", Timestamp.from(now)));
        return inserted > 0;
    }

    // The consent of the latest update still to be handled that names one of these artefacts. A withdrawal naming only
    // artefacts is stored under it, so it waits behind the grant that makes them, as a data push does.
    public Optional<String> pendingConsentKeyOf(long organisationId, Collection<String> artefactIds) {
        Long latestId = null;
        String key = null;
        for (String artefactId : artefactIds) {
            if (artefactId == null || artefactId.isBlank()) continue;
            List<Map<String, Object>> rows = jdbc.queryForList("select id, consent_key from abdm_inbound_event where organisation_id = :org " +
                            "and route = '" + HOLDING_ROUTE + "' and status in ('RECEIVED', 'IN_PROGRESS') and consent_key is not null " +
                            "and strpos(body, :quoted) > 0 order by id desc limit 1",
                    new MapSqlParameterSource("org", organisationId).addValue("quoted", "\"" + artefactId + "\"", Types.VARCHAR));
            for (Map<String, Object> row : rows) {
                long id = ((Number) row.get("id")).longValue();
                if (latestId == null || id > latestId) {
                    latestId = id;
                    key = (String) row.get("consent_key");
                }
            }
        }
        return Optional.ofNullable(key);
    }

    // Up to max due events, oldest first, each marked in progress and counted as a try; locked rows are left to whoever holds them.
    public List<Event> claim(int max, Instant now) {
        List<Event> events = jdbc.query(CLAIM, new MapSqlParameterSource("now", Timestamp.from(now)).addValue("max", max), EVENT);
        return events.stream().sorted(Comparator.comparingLong(Event::id)).toList();
    }

    // Each outcome below is written only while the event is still held by the claim that took it: one swept back past the
    // timeout and taken again belongs to the later try, and a slow worker's outcome changes nothing.
    private static final String STILL_HELD = "id = :id and status = 'IN_PROGRESS' and claimed_at = :claimedAt";

    // The body is not kept once handled: it can carry a patient's details, and the traffic log keeps a redacted copy.
    public void done(Event event) {
        jdbc.update("update abdm_inbound_event set status = 'DONE', body = null, last_error = null, claimed_at = null, updated_at = now() " +
                "where " + STILL_HELD, held(event));
    }

    // Back for a later try, or FAILED when this was the last; true when it is FAILED. A FAILED row keeps no body.
    public boolean retry(Event event, String error, Instant now) {
        boolean last = event.attempts() >= MAX_ATTEMPTS;
        int written = jdbc.update("update abdm_inbound_event set status = :status, next_attempt_at = :next, claimed_at = null, last_error = :error, " +
                        "body = case when :status = 'FAILED' then null else body end, updated_at = :now where " + STILL_HELD,
                held(event).addValue("status", last ? FAILED : RECEIVED, Types.VARCHAR)
                        .addValue("next", Timestamp.from(now.plus(waitAfter(event.attempts()))))
                        .addValue("error", bounded(error), Types.VARCHAR).addValue("now", Timestamp.from(now)));
        return last && written > 0;
    }

    // Back for a later try, never FAILED, keeping its body: past the cap every PAST_CAP_WAIT.
    public void retryUntilHandled(Event event, String error, Instant now) {
        Duration wait = event.attempts() >= MAX_ATTEMPTS ? PAST_CAP_WAIT : waitAfter(event.attempts());
        jdbc.update("update abdm_inbound_event set status = 'RECEIVED', next_attempt_at = :next, claimed_at = null, last_error = :error, " +
                        "updated_at = :now where " + STILL_HELD,
                held(event).addValue("next", Timestamp.from(now.plus(wait)))
                        .addValue("error", bounded(error), Types.VARCHAR).addValue("now", Timestamp.from(now)));
    }

    // Never to be handled, so its body, which can carry a patient's details, is not kept.
    public void fail(Event event, String error, Instant now) {
        jdbc.update("update abdm_inbound_event set status = 'FAILED', body = null, claimed_at = null, last_error = :error, updated_at = :now " +
                        "where " + STILL_HELD,
                held(event).addValue("error", bounded(error), Types.VARCHAR).addValue("now", Timestamp.from(now)));
    }

    private static MapSqlParameterSource held(Event event) {
        // The claim time as the claim returned it, to the microsecond Postgres keeps.
        return new MapSqlParameterSource("id", event.id()).addValue("claimedAt", event.claimedAt() == null ? null : Timestamp.from(event.claimedAt()));
    }

    // Taken but not handled, as a drain out of time leaves it: back as it was, due at once, its try not counted.
    public void release(Event event) {
        jdbc.update("update abdm_inbound_event set status = 'RECEIVED', attempts = greatest(attempts - 1, 0), claimed_at = null, " +
                "updated_at = now() where " + STILL_HELD, held(event));
    }

    // Rows in progress past the timeout were left by a server that stopped: back for another try, or FAILED on their last.
    // A consent update is always put back: whether it may be given up depends on what it says, which its next try reads.
    public Sweep sweep(Instant now) {
        String givenUp = "attempts >= :max and coalesce(route, '') <> '" + HOLDING_ROUTE + "'";
        List<String> statuses = jdbc.queryForList("update abdm_inbound_event set " +
                        "status = case when " + givenUp + " then 'FAILED' else 'RECEIVED' end, next_attempt_at = :now, claimed_at = null, " +
                        "body = case when " + givenUp + " then null else body end, " +
                        "last_error = 'Left in progress past " + IN_PROGRESS_TIMEOUT.toMinutes() + " minutes', updated_at = :now " +
                        "where status = 'IN_PROGRESS' and claimed_at < :staleBefore returning status",
                new MapSqlParameterSource("max", MAX_ATTEMPTS).addValue("now", Timestamp.from(now))
                        .addValue("staleBefore", Timestamp.from(now.minus(IN_PROGRESS_TIMEOUT))), String.class);
        int failed = (int) statuses.stream().filter(FAILED::equals).count();
        return new Sweep(statuses.size() - failed, failed);
    }

    // Organisations with handled or given-up events received before the cutoff, for the retention purge, which is owed
    // whatever their switches. Across organisations: called by the job runner before it acts as each.
    public List<Long> organisationsWithFinishedEventsBefore(Instant before) {
        return jdbc.queryForList("select distinct organisation_id from abdm_inbound_event where status in ('DONE', 'FAILED') " +
                "and received_at < :before order by 1", new MapSqlParameterSource("before", Timestamp.from(before)), Long.class);
    }

    // Handled and given-up events of the organisation the thread acts for, received before the cutoff.
    public int purgeFinishedOlderThan(Instant before) {
        Long organisationId = orgContext.organisationId();
        return organisationId == null ? 0 : purgeFinishedOlderThan(organisationId, before);
    }

    // The same for the organisation named, needing no user (AbdmJobRunner's work without a login).
    public int purgeFinishedOlderThan(long organisationId, Instant before) {
        return jdbc.update("delete from abdm_inbound_event where organisation_id = :org and status in ('DONE', 'FAILED') and received_at < :before",
                new MapSqlParameterSource("org", organisationId).addValue("before", Timestamp.from(before)));
    }

    static Duration waitAfter(int attempts) {
        Duration wait = FIRST_WAIT.multipliedBy(1L << Math.min(Math.max(attempts - 1, 0), 20));
        return wait.compareTo(MAX_WAIT) > 0 ? MAX_WAIT : wait;
    }

    private static String bounded(String error) {
        if (error == null) return null;
        return error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
    }
}

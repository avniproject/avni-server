package org.avni.server.service.abdm;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

// Each organisation's last success and last error of each ABDM job (abdm_job_run), for the admin page's status. Kept in
// the database so every server and every restart reports the same. Written by the job runner, which acts as no
// organisation; read as the organisation.
@Component
public class AbdmJobRuns {
    private static final int MAX_ERROR = 1000;

    private final NamedParameterJdbcTemplate jdbc;

    public AbdmJobRuns(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // A success clears the error: lastError is the error of the latest run, when that run failed.
    public void succeeded(long organisationId, String task, Instant at) {
        jdbc.update("insert into abdm_job_run (organisation_id, task, last_success_at) values (:org, :task, :at) " +
                "on conflict (organisation_id, task) do update set last_success_at = excluded.last_success_at, last_error = null, last_error_at = null",
                params(organisationId, task, at));
    }

    public void failed(long organisationId, String task, String error, Instant at) {
        String kept = error == null ? "failed" : error.length() > MAX_ERROR ? error.substring(0, MAX_ERROR) : error;
        jdbc.update("insert into abdm_job_run (organisation_id, task, last_error, last_error_at) values (:org, :task, :error, :at) " +
                "on conflict (organisation_id, task) do update set last_error = excluded.last_error, last_error_at = excluded.last_error_at",
                params(organisationId, task, at).addValue("error", kept, Types.VARCHAR));
    }

    // {task: {lastSuccessAt, lastError}} of the organisation.
    public Map<String, Map<String, Object>> of(long organisationId) {
        Map<String, Map<String, Object>> jobs = new LinkedHashMap<>();
        jdbc.query("select task, last_success_at, last_error from abdm_job_run where organisation_id = :org order by task",
                new MapSqlParameterSource("org", organisationId), rs -> {
                    Map<String, Object> job = new LinkedHashMap<>();
                    Timestamp lastSuccessAt = rs.getTimestamp("last_success_at");
                    job.put("lastSuccessAt", lastSuccessAt == null ? null : AbdmTime.format(lastSuccessAt.toInstant()));
                    job.put("lastError", rs.getString("last_error"));
                    jobs.put(rs.getString("task"), job);
                });
        return jobs;
    }

    private static MapSqlParameterSource params(long organisationId, String task, Instant at) {
        return new MapSqlParameterSource("org", organisationId).addValue("task", task, Types.VARCHAR)
                .addValue("at", Timestamp.from(at));
    }
}

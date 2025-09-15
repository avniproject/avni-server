package org.avni.server.dao;

import org.avni.server.domain.User;
import org.avni.server.domain.batch.BatchJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class AvniJobRepository {

    private NamedParameterJdbcTemplate jdbcTemplate;

    public AvniJobRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Page<JobStatus> getJobStatuses(User user, String jobFilterCondition, Pageable pageable) {
        Integer pageNumber = pageable.getPageNumber();
        Integer pageSize = pageable.getPageSize();
        Integer offset = pageNumber * pageSize;
        Integer limit = pageSize;
        Long userId = user.getId();
        String basicQuery = "select * from\n" +
                "(select bje.job_execution_id execution_id,\n" +
                "       bje.status status,\n" +
                "       bje.exit_code exit_code,\n" +
                "       bje.create_time create_time,\n" +
                "       bje.start_time start_time,\n" +
                "       bje.end_time end_time,\n" +
                "       string_agg(case when bjep.parameter_name = 'uuid' then bjep.parameter_value else '' end::text, '') uuid,\n" +
                "       string_agg(case when bjep.parameter_name = 'fileName' then bjep.parameter_value else '' end::text, '') fileName,\n" +
                "       sum(case when bjep.parameter_name = 'noOfLines' then (case when bjep.parameter_value = '' then 0 else bjep.parameter_value::int end) else 0 end) noOfLines,\n" +
                "       string_agg(case when bjep.parameter_name = 's3Key' then bjep.parameter_value else '' end::text, '') s3Key,\n" +
                "       sum(case when bjep.parameter_name = 'userId' then (case when bjep.parameter_value = '' then 0 else bjep.parameter_value::int end) else 0 end) userId,\n" +
                "       string_agg(case when bjep.parameter_name = 'type' then bjep.parameter_value::text else '' end::text, '') job_type,\n" +
                "       max(case when bjep.parameter_name = 'startDate' then bjep.parameter_value::timestamp else null::timestamp end::timestamp) startDate,\n" +
                "       max(case when bjep.parameter_name = 'endDate' then bjep.parameter_value::timestamp else null::timestamp end::timestamp) endDate,\n" +
                "       string_agg(case when bjep.parameter_name = 'subjectTypeUUID' then bjep.parameter_value::text else '' end::text, '') subjectTypeUUID,\n" +
                "       string_agg(case when bjep.parameter_name = 'programUUID' then bjep.parameter_value::text else '' end::text, '') programUUID,\n" +
                "       string_agg(case when bjep.parameter_name = 'encounterTypeUUID' then bjep.parameter_value::text else '' end::text, '') encounterTypeUUID,\n" +
                "       string_agg(case when bjep.parameter_name = 'reportType' then bjep.parameter_value::text else '' end::text, '') reportType,\n" +
                "       max(bse.read_count) read_count,\n" +
                "       max(bse.write_count) write_count,\n" +
                "       max(bse.write_skip_count) write_skip_count\n" +
                "from batch_job_execution bje\n" +
                "left outer join  batch_job_execution_params bjep on bje.job_execution_id = bjep.job_execution_id\n" +
                "left outer join batch_step_execution bse on bje.job_execution_id = bse.job_execution_id\n" +
                "group by bje.job_execution_id, bje.status, bje.exit_code, bje.create_time, bje.start_time, bje.end_time\n" +
                "order by bje.create_time desc) jobs\n" +
                "where jobs.userId = :userId\n";

        String jobFilterQuery = basicQuery.concat(jobFilterCondition);
        String query = jobFilterQuery.concat(" offset :offset limit :limit;");
        Map<String, Object> jobStatusParams = new HashMap<>();
        jobStatusParams.put("limit", limit);
        jobStatusParams.put("offset", offset);
        jobStatusParams.put("userId", userId);
        List<JobStatus> jobStatuses = jdbcTemplate.query(query, jobStatusParams, new JobStatusMapper());

        String countQuery = String.format("select count(*) from (%s) items;", jobFilterQuery);
        Map<String, Object> countParams = new HashMap<>();
        countParams.put("userId", userId);
        Long count = jdbcTemplate.queryForObject(countQuery, countParams, Long.class);

        return new PageImpl<>(jobStatuses, pageable, count);
    }

    public BatchJobStatus getJobStatus(String jobName, String parameterName, String parameterValue) {
        String baseQuery = """
                                select bje.status, bje.create_time createDateTime, bje.start_time startDateTime, bje.end_time endDateTime, bje.exit_message exitMessage, bje.exit_code exitCode
                                    from batch_job_instance i
                                         left join batch_job_execution bje on i.job_instance_id = bje.job_instance_id
                                         left join batch_job_execution_params bjep on bje.job_execution_id = bjep.job_execution_id
                                where i.job_name = :jobName
                                  and create_time is not null
                                  and bjep.parameter_name = :parameterName and bjep.parameter_value = :parameterValue
                                order by create_time desc
                                limit 1;
                """;
        Map<String, Object> params = new HashMap<>();
        params.put("jobName", jobName);
        params.put("parameterName", parameterName);
        params.put("parameterValue", parameterValue);
        List<BatchJobStatus> statuses = jdbcTemplate.query(baseQuery, params, (rs, rowNum) ->
                new BatchJobStatus(rs.getString(1), rs.getTimestamp(2), rs.getTimestamp(3), rs.getTimestamp(4), rs.getString(5), rs.getString(6)));
        if (statuses.isEmpty()) {
            return new BatchJobStatus("NOT_FOUND", null, null, null, null, null);
        }
        return statuses.get(0);
    }
}

package org.avni.server.dao.search;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.avni.server.application.OrganisationConfigSettingKey;
import org.avni.server.dao.ConceptRepository;
import org.avni.server.dao.SubjectTypeRepository;
import org.avni.server.domain.ConceptDataType;
import org.avni.server.domain.SubjectType;
import org.avni.server.framework.ApplicationContextProvider;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.service.OrganisationConfigService;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.util.S;
import org.avni.server.web.request.webapp.search.*;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

public class BaseSubjectSearchQueryBuilder<T> {
    private final Logger logger;

    protected static final String ENCOUNTER_FILTER = "i.id in (select e.individual_id from encounter e where e.encounter_date_time is not null and e.is_voided is false\n";
    protected static final String PROGRAM_ENROLMENT_FILTER = "i.id in (select penr.individual_id from program_enrolment penr where penr.enrolment_date_time is not null and penr.is_voided is false\n";
    protected static final String PROGRAM_ENCOUNTER_FILTER = "i.id in (select penr.individual_id from program_enrolment penr join program_encounter penc on penc.program_enrolment_id = penr.id where penc.encounter_date_time is not null and penc.is_voided is false and penr.is_voided is false\n";
    public static final String FULL_NAME = "concat_ws(' ', nullif(i.first_name, ''), nullif(i.middle_name, ''), nullif(i.last_name, ''))";
    private static final String NUMBER_PATTERN = "-?[0-9]{1,30}(\\.[0-9]{1,30})?";
    private static final String UUID_PATTERN = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";
    protected static final String ADDRESS_FILTER = "i.address_id in (select al.id from address_level al where 1=1\n";

    private String orderByClause = "";

    protected final Set<String> whereClauses = new HashSet<>();
    private final Map<String, Object> parameters = new HashMap<>();
    private boolean forCount;
    private final Set<String> customFields = new HashSet<>();
    public static final String DEFAULT_TIME_ZONE = "Asia/Kolkata";
    private String timeZone = DEFAULT_TIME_ZONE;

    public BaseSubjectSearchQueryBuilder() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    public SqlQuery buildUsingBaseQuery(String baseQuery, String groupByClause) {
        StringBuilder query = new StringBuilder();
        query.append(baseQuery);

        query.append(String.format("\n where i.organisation_id = %d and\n", UserContextHolder.getOrganisation().getId()));

        query.append(String.join(" \nand ", whereClauses));
        query.append(groupByClause);
        if (parameters.get("offset") == null || parameters.get("limit") == null) {
            addDefaultPaginationFilters();
        }

        String finalQuery = "";
        if (forCount) {
            finalQuery = "select count(*) from (" + query + ") a";
            removePaginationFilters();
        } else {
            String offsetLimitClause = "offset :offset limit :limit";
            finalQuery = query.append("\n")
                    .append(orderByClause)
                    .append("\n")
                    .append(offsetLimitClause)
                    .toString();
        }
        String customFieldString = customFields.isEmpty() ? "" : ",\n".concat(String.join(",\n", customFields));
        String queryWithCustomFields = finalQuery.replace(" $CUSTOM_FIELDS", customFieldString);
        logger.trace(parameters.toString());
        return new SqlQuery(queryWithCustomFields, parameters);
    }

    private void addDefaultPaginationFilters() {
        parameters.put("offset", 0);
        parameters.put("limit", 10);
    }

    private void removePaginationFilters() {
        parameters.remove("offset");
        parameters.remove("limit");
    }

    protected T withPaginationFilters(PageDetails pageElement) {
        int offset = 0, limit = 10;
        if (pageElement.getNumberOfRecordPerPage() != null) {
            limit = pageElement.getNumberOfRecordPerPage();
            if (pageElement.getPageNumber() != null) {
                offset = pageElement.getNumberOfRecordPerPage() * pageElement.getPageNumber();
            }
        }
        parameters.put("offset", offset);
        parameters.put("limit", limit);

        String sortColumn = pageElement.getSortColumn();
        if (!StringUtils.isEmpty(sortColumn)) {
            SortOrder sortOrder = Optional.ofNullable(pageElement.getSortOrder()).orElse(SortOrder.asc);

            Map<String, String> columnsMap = new HashMap<String, String>() {
                {
                    put("ID", "i.id");
                    put("FULLNAME", FULL_NAME);
                    put("SUBJECTTYPE", "st.name");
                    put("GENDER", "gender.name");
                    put("DATEOFBIRTH", "i.date_of_birth");
                    put("TITLE_LINEAGE", "tllv.title_lineage");
                }
            };

            String sortExpression = sortColumn.equalsIgnoreCase("AGE")
                    ? "i.date_of_birth " + (sortOrder == SortOrder.asc ? SortOrder.desc : SortOrder.asc).name() + " nulls last"
                    : columnsMap.get(sortColumn.toUpperCase()) + " " + sortOrder.name();
            this.orderByClause = "order by " + sortExpression + ", i.id desc";
        }

        return (T) this;
    }

    public T withCustomFields(String subjectType) {
        OrganisationConfigService organisationConfigService = ApplicationContextProvider.getContext().getBean(OrganisationConfigService.class);
        ObjectMapper objectMapper = ObjectMapperSingleton.getObjectMapper();
        Object searchResultFields = organisationConfigService.getSettingsByKey(OrganisationConfigSettingKey.searchResultFields.toString());
        List<CustomSearchFields> customSearchFields = objectMapper.convertValue(searchResultFields, new TypeReference<List<CustomSearchFields>>() {
        });
        if (customSearchFields.isEmpty()) {
            return (T) this;
        }
        List<CustomSearchFields> searchFieldsForSubject = getSearchFields(subjectType, customSearchFields);
        ConceptRepository conceptRepository = ApplicationContextProvider.getContext().getBean(ConceptRepository.class);
        searchFieldsForSubject.forEach(sf -> {
            List<SearchResultConcepts> searchResultConcepts = sf.getSearchResultConcepts();
            searchResultConcepts.forEach(c -> {
                org.avni.server.domain.Concept concept = conceptRepository.findByUuid(c.getUuid());
                if (!concept.isHidden()) {
                    addCustomFields(concept);
                }
            });
        });
        return (T) this;
    }

    protected void addCustomFields(org.avni.server.domain.Concept concept) {
        String alias = escapeSqlIdentifier(concept.getName());
        if (concept.isCoded()) {
            customFields.add(String.format("multi_select_coded(i.observations -> '%s') as \"%s\"", concept.getUuid(), alias));
        } else if (concept.getDataType().equals(ConceptDataType.Date.toString())) {
            customFields.add(String.format("%s as \"%s\"", observationAsDate(String.format("i.observations ->> '%s'", concept.getUuid())), alias));
        } else {
            customFields.add(String.format("i.observations ->> '%s' as \"%s\"", concept.getUuid(), alias));
        }
    }

    // The concept name is interpolated as a SQL column alias (as "..."). Double any embedded
    // double-quote so the name stays one identifier — prevents SQL injection via a crafted
    // concept name, and the plain breakage when a legitimate name contains a quote.
    private static String escapeSqlIdentifier(String identifier) {
        return identifier == null ? null : identifier.replace("\"", "\"\"");
    }

    private List<CustomSearchFields> getSearchFields(String subjectTypeUUID, List<CustomSearchFields> customSearchFields) {
        if (S.isEmpty(subjectTypeUUID)) return customSearchFields;
        SubjectTypeRepository subjectTypeRepository = ApplicationContextProvider.getContext().getBean(SubjectTypeRepository.class);
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        return customSearchFields.stream().filter(csf -> csf.getSubjectTypeUUID().equals(subjectType.getUuid())).collect(Collectors.toList());
    }

    public T withTimeZone(String timeZone) {
        if (S.isEmpty(timeZone)) return (T) this;
        try {
            this.timeZone = ZoneId.of(timeZone).getId();
        } catch (DateTimeException e) {
            logger.warn("Ignoring unknown time zone {} in search", timeZone);
        }
        return (T) this;
    }

    // A stored answer that isn't a valid date or time (empty, or hand-edited / uploaded text) is treated as no answer,
    // so one bad record can't fail the whole search (avni-webapp#965). The patterns accept only real calendar dates
    // (days per month, 29 February in leap years only), so the casts below never see a value they would reject.
    // Postgres evaluates a case lazily, so a cast only runs on a value its pattern accepted.
    private static final String CALENDAR_DATE = "([1-9][0-9]{3}-((0[13578]|1[02])-(0[1-9]|[12][0-9]|3[01])|(0[469]|11)-(0[1-9]|[12][0-9]|30)|02-(0[1-9]|1[0-9]|2[0-8]))"
            + "|([1-9][0-9](0[48]|[2468][048]|[13579][26])|([2468][048]|[13579][26])00)-02-29)";
    private static final String TIME_OF_DAY = "([01][0-9]|2[0-3]):[0-5][0-9](:[0-5][0-9](\\.[0-9]+)?)?";
    // Offsets stop at ±15:59, the most Postgres reads; real ones go to ±14:00.
    static final String DATE_TIME_PATTERN = "^" + CALENDAR_DATE + "[Tt ]" + TIME_OF_DAY + "(Z|[+-](0[0-9]|1[0-5])(:?[0-5][0-9])?)?$";
    static final String DATE_PATTERN = "^" + CALENDAR_DATE + "$";
    // CSV upload saves a Time cell as typed, so a Time answer can be 9:30, 9:30:00 PM or 9:30am, with spaces around it.
    // Accepted: a 24-hour time with a one- or two-digit hour, or a 12-hour time with AM/PM, all of which Postgres reads.
    private static final String SECONDS = "(:[0-5][0-9](\\.[0-9]+)?)?";
    static final String TIME_PATTERN = "^ *((0?[0-9]|1[0-9]|2[0-3]):[0-5][0-9]" + SECONDS + "|(0?[0-9]|1[0-2]):[0-5][0-9]" + SECONDS + " *[aApP][mM]) *$";

    private String observationAsDate(String observationValue) {
        return String.format("(case when %1$s ~ '%3$s' then cast(cast(%1$s as timestamp with time zone) at time zone '%2$s' as date)" +
                        " when %1$s ~ '%4$s' then cast(%1$s as date) end)",
                observationValue, timeZone, DATE_TIME_PATTERN, DATE_PATTERN);
    }

    private static String observationAsTime(String observationValue) {
        return String.format("(case when %1$s ~ '%2$s' then cast(%1$s as time) end)", observationValue, TIME_PATTERN);
    }

    // Typed text is matched as written: % and _ are not wildcards, as on the phone.
    static String containsPattern(String value) {
        return "%" + value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    protected String localDate(String timestampColumn) {
        return String.format("cast(%s at time zone '%s' as date)", timestampColumn, timeZone);
    }

    private String parameterAsDate(String param, String value) {
        return value.contains("T")
                ? String.format("cast(cast(:%s as timestamp with time zone) at time zone '%s' as date)", param, timeZone)
                : String.format("cast(:%s as date)", param);
    }

    /**
     * A DateTime filter compares dates in the user's time zone, not moments: From and To on the same day match any
     * answer on that day, whatever time the date picker sent (avni-webapp#965).
     */
    private String dateTimeComparison(String observation, String operator, String param, String value) {
        return String.format("%s %s %s", observationAsDate(observation), operator, parameterAsDate(param, value));
    }

    public T withNameFilter(String name) {
        if (!StringUtils.isEmpty(name)) {
            String[] tokens = name.split("\\s+");
            StringBuffer whereClause = new StringBuffer();
            whereClause.append("(");
            List<String> clauses = new ArrayList<>();
            for (int i = 0; i < tokens.length; i++) {
                String token = "%" + tokens[i] + "%";
                String parameter = "subjectSearchToken" + i;
                addParameter(parameter, token);
                clauses.add("    (i.first_name ilike :" + parameter + "\n" +
                        "        or i.middle_name ilike :" + parameter + "\n" +
                        "        or i.last_name ilike :" + parameter + ")");
            }
            whereClause.append(String.join(" and ", clauses));
            whereClause.append(")");
            whereClauses.add(whereClause.toString());
        }
        return (T) this;
    }

    public T withAgeFilter(IntegerRange ageRange) {
        if (ageRange == null || ageRange.getMinValue() == null) return (T) this;
        parameters.put("age", ageRange.getMinValue());
        whereClauses.add("cast(date_part(cast('year' as text), age(now(), cast(i.date_of_birth as timestamp with time zone))) as numeric) = :age");
        return (T) this;
    }

    public T withDateOfBirthFilter(LocalDate dateOfBirth) {
        if (dateOfBirth == null) return (T) this;
        addParameter("dateOfBirth", dateOfBirth.toString());
        whereClauses.add("i.date_of_birth = cast(:dateOfBirth as date)");
        return (T) this;
    }

    public T withGenderFilter(List<String> genders) {
        if (genders == null || genders.isEmpty()) return (T) this;
        String parameter = "genders";
        addParameter(parameter, genders);
        whereClauses.add("gender.uuid in :genders");
        return (T) this;
    }

    public T withRegistrationDateFilter(DateRange registrationDateRange) {
        if (registrationDateRange == null || registrationDateRange.isEmpty()) return (T) this;

        return withRangeFilter(registrationDateRange,
                "registrationDate",
                "i.registration_date >= cast(:rangeParam as date)",
                "i.registration_date <= cast(:rangeParam as date)", "");
    }

    public T withIncludeVoidedFilter(Boolean includeVoided) {
        if (includeVoided == null) return (T) this;
        if (!includeVoided) {
            whereClauses.add("i.is_voided is false");
        }
        return (T) this;
    }

    public T withAddressIdsFilter(List<Integer> addressIds) {
        if (addressIds == null || addressIds.isEmpty()) return (T) this;
        String addressIdString = addressIds.stream().map(String::valueOf)
                .collect(Collectors.joining("|"));
        parameters.put("addressLQuery", String.format("*.%s.*", addressIdString));
        whereClauses.add(generateWhereClause(ADDRESS_FILTER, "al.lineage ~ cast(:addressLQuery as lquery)"));
        return (T) this;
    }

    public T withSearchAll(String typedText) {
        String searchString = typedText == null ? null : typedText.replace("\u0000", "");
        if (StringUtils.isEmpty(searchString)) return (T) this;
        parameters.put("searchAll", containsPattern(searchString));
        parameters.put("searchAllStored", containsPattern(asStoredInJson(searchString)));
        parameters.put("searchAllPath", searchAllPath(searchString));
        whereClauses.add("(" + FULL_NAME + " ilike :searchAll" +
                " or " + answersContainSearchAll("i.observations") +
                " or i.id in (select penr.individual_id from program_enrolment penr where penr.is_voided is false and " + answersContainSearchAll("penr.observations") + "))");
        return (T) this;
    }

    private static String answersContainSearchAll(String observations) {
        return String.format("(case when cast(%1$s as text) ilike :searchAllStored then jsonb_path_exists(%1$s, cast(:searchAllPath as jsonpath), '{}', true) else false end)",
                observations);
    }

    static String asStoredInJson(String value) {
        StringBuilder stored = new StringBuilder();
        value.chars().forEach(c -> {
            switch (c) {
                case '"' -> stored.append("\\\"");
                case '\\' -> stored.append("\\\\");
                case '\b' -> stored.append("\\b");
                case '\f' -> stored.append("\\f");
                case '\n' -> stored.append("\\n");
                case '\r' -> stored.append("\\r");
                case '\t' -> stored.append("\\t");
                default -> {
                    if (c < 0x20) stored.append(String.format("\\u%04x", c));
                    else stored.append((char) c);
                }
            }
        });
        return stored.toString();
    }

    static String searchAllPath(String searchString) {
        StringBuilder path = new StringBuilder("strict $.** ? ((@.type() == \"string\" && !(@ like_regex \"")
                .append(UUID_PATTERN).append("\") && @ like_regex ").append(jsonPathString(searchString)).append(" flag \"iq\")");
        if (searchString.matches(NUMBER_PATTERN)) {
            path.append(" || (@.type() == \"number\" && @ == ").append(new BigDecimal(searchString).toPlainString()).append(")");
        }
        if (searchString.equalsIgnoreCase("true") || searchString.equalsIgnoreCase("false")) {
            path.append(" || (@.type() == \"boolean\" && @ == ").append(searchString.toLowerCase()).append(")");
        }
        return path.append(")").toString();
    }

    private static String jsonPathString(String value) {
        StringBuilder literal = new StringBuilder("\"");
        value.chars().forEach(c -> {
            if (c == '"' || c == '\\') literal.append('\\').append((char) c);
            else if (c < 0x20) literal.append(String.format("\\u%04x", c));
            else literal.append((char) c);
        });
        return literal.append("\"").toString();
    }

    private String getFilterForSearchScope(String searchScope) {
        if (searchScope.equalsIgnoreCase("encounter")) {
            return ENCOUNTER_FILTER;
        }
        if (searchScope.equalsIgnoreCase("programEnrolment")) {
            return PROGRAM_ENROLMENT_FILTER;
        }
        if (searchScope.equalsIgnoreCase("programEncounter")) {
            return PROGRAM_ENCOUNTER_FILTER;
        }
        return "";
    }

    protected String generateWhereClause(String filterWithDefaultConditions, String moreConditions) {
        if (!StringUtils.isEmpty(filterWithDefaultConditions)) {
            return filterWithDefaultConditions + " and " + moreConditions + ")";
        } else {
            return moreConditions;
        }
    }

    public T withConceptsFilter(List<Concept> concept) {
        if (concept == null || concept.isEmpty()) return (T) this;
        Map<String, String> aliasMap = new HashMap<String, String>() {
            {
                put("REGISTRATION", "i");
                put("PROGRAMENROLMENT", "penr");
                put("PROGRAMENCOUNTER", "penc");
                put("ENCOUNTER", "e");
            }
        };

        for (int ci = 0; ci < concept.size(); ci++) {
            Concept c = concept.get(ci);
            String conceptUuidParam = "conceptUuid" + ci;
            addParameter(conceptUuidParam, c.getUuid());
            String tableAlias = aliasMap.get(c.getSearchScope().toUpperCase());
            int clausesBefore = whereClauses.size();

            if (c.getDataType().equalsIgnoreCase("CODED")) {
                List<String> conditions = new ArrayList<>();
                for (int i = 0; i < c.getValues().size(); i++) {
                    String value = c.getValues().get(i);
                    String param = "codedConceptValue" + ci + "00" + i;
                    addParameter(param, value);
                    String sql = "case when JSONB_TYPEOF(" + tableAlias + ".observations->:" + conceptUuidParam + ") = 'array'" +
                            "then " +
                            tableAlias + ".observations -> :" + conceptUuidParam + " @> to_jsonb(cast(:" + param + " as text)) " +
                            "else " +
                            tableAlias + ".observations ->> :" + conceptUuidParam + "= :" + param + " end";
                    conditions.add(sql);
                }
                String codedFilter = String.join(" or ", conditions);
                whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), "(" + codedFilter + ")"));
            }

            if (c.getDataType().equalsIgnoreCase("TEXT") || c.getDataType().equalsIgnoreCase("ID") || c.getDataType().equalsIgnoreCase("NOTES")) {
                String value = containsPattern(c.getValue());
                String param = "textValue" + ci;
                addParameter(param, value);
                whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), tableAlias + ".observations->> :" + conceptUuidParam + " ilike :" + param));
            }

            if (c.getDataType().equalsIgnoreCase("NUMERIC")) {
                if (c.getWidget() != null && c.getWidget().equalsIgnoreCase("RANGE")) {
                    if (!StringUtils.isEmpty(c.getMinValue())) {
                        BigDecimal value = new BigDecimal(c.getMinValue());
                        String param = "numericValueMin" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->> :" + conceptUuidParam + " as numeric)  >= :" + param));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        BigDecimal value = new BigDecimal(c.getMaxValue());
                        String param = "numericValueMax" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as numeric)  <= :" + param));
                    }
                } else {
                    BigDecimal value = new BigDecimal(c.getMinValue());
                    String param = "numericValueMin" + ci;
                    addParameter(param, value);
                    whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as numeric)  = :" + param));
                }
            }

            if (c.getDataType().equalsIgnoreCase("DATE")) {
                if (c.getWidget() != null && c.getWidget().equalsIgnoreCase("RANGE")) {
                    if (!StringUtils.isEmpty(c.getMinValue())) {
                        String value = c.getMinValue();
                        String param = "dateTimeValueMin" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),observationAsDate(tableAlias + ".observations ->>:" + conceptUuidParam) + "  >= cast(:" + param + " as date)"));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        String value = c.getMaxValue();
                        String param = "dateTimeValueMax" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),observationAsDate(tableAlias + ".observations ->>:" + conceptUuidParam) + "  <= cast(:" + param + " as date)"));
                    }
                } else {
                    String value = c.getMinValue();
                    String param = "dateTimeValueMin" + ci;
                    addParameter(param, value);
                    whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),observationAsDate(tableAlias + ".observations ->>:" + conceptUuidParam) + "  = cast(:" + param + " as date)"));
                }
            }

            if (c.getDataType().equalsIgnoreCase("DATETIME")) {
                String observation = tableAlias + ".observations ->>:" + conceptUuidParam;
                if (c.getWidget() != null && c.getWidget().equalsIgnoreCase("RANGE")) {
                    if (!StringUtils.isEmpty(c.getMinValue())) {
                        String param = "dateTimeValueMin" + ci;
                        addParameter(param, c.getMinValue());
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), dateTimeComparison(observation, ">=", param, c.getMinValue())));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        String param = "dateTimeValueMax" + ci;
                        addParameter(param, c.getMaxValue());
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), dateTimeComparison(observation, "<=", param, c.getMaxValue())));
                    }
                } else if (!StringUtils.isEmpty(c.getMinValue())) {
                    String param = "dateTimeValueMin" + ci;
                    addParameter(param, c.getMinValue());
                    whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), observationAsDate(observation) + " = " + parameterAsDate(param, c.getMinValue())));
                }
            }

            if (c.getDataType().equalsIgnoreCase("TIME")) {
                String observation = observationAsTime(tableAlias + ".observations ->>:" + conceptUuidParam);
                if (c.getWidget() != null && c.getWidget().equalsIgnoreCase("RANGE")) {
                    if (!StringUtils.isEmpty(c.getMinValue())) {
                        String param = "timeValueMin" + ci;
                        addParameter(param, c.getMinValue());
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), observation + " >= cast(:" + param + " as time)"));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        String param = "timeValueMax" + ci;
                        addParameter(param, c.getMaxValue());
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), observation + " <= cast(:" + param + " as time)"));
                    }
                } else if (!StringUtils.isEmpty(c.getMinValue())) {
                    String param = "timeValueMin" + ci;
                    addParameter(param, c.getMinValue());
                    whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), observation + " = cast(:" + param + " as time)"));
                }
            }

            if (whereClauses.size() == clausesBefore) {
                parameters.remove(conceptUuidParam);
                logger.warn("Search filter on concept {} of type {} is not supported and was ignored", c.getUuid(), c.getDataType());
            }
        }

        return (T) this;
    }

    /**
     * Programme filter of Data Entry App search.
     * <ul>
     *     <li>programs: the subject has a non-voided enrolment in one of them. An exited enrolment matches only when includeExited is set.</li>
     *     <li>includeExited with no programme ticked: the subject has exited (non-voided enrolment with an exit date) from any programme.</li>
     *     <li>notEnrolled: the subject has no non-voided enrolment, in any programme, exited or not.</li>
     *     <li>more than one: any of the conditions.</li>
     * </ul>
     * Voided (cancelled) enrolments never count. With nothing ticked, the filter is ignored.
     */
    public T withProgramsFilter(List<String> programUuids, boolean includeExited, boolean notEnrolled) {
        boolean hasPrograms = programUuids != null && !programUuids.isEmpty();
        if (!hasPrograms && !includeExited && !notEnrolled) return (T) this;

        List<String> conditions = new ArrayList<>();
        if (hasPrograms) {
            addParameter("programFilterUuids", programUuids);
            conditions.add("exists (select 1 from program_enrolment pf_penr join program pf_p on pf_p.id = pf_penr.program_id" +
                    " where pf_penr.individual_id = i.id and pf_penr.is_voided is false and pf_p.uuid in (:programFilterUuids)" +
                    (includeExited ? "" : " and pf_penr.program_exit_date_time is null") + ")");
        } else if (includeExited) {
            conditions.add("exists (select 1 from program_enrolment pf_exit where pf_exit.individual_id = i.id and pf_exit.is_voided is false and pf_exit.program_exit_date_time is not null)");
        }
        if (notEnrolled) {
            conditions.add("not exists (select 1 from program_enrolment pf_none where pf_none.individual_id = i.id and pf_none.is_voided is false)");
        }
        whereClauses.add("(" + String.join(" or ", conditions) + ")");
        return (T) this;
    }

    protected T withRangeFilter(RangeFilter rangeFilter, String parameterPrefix, String minFilter, String maxFilter, String filter) {
        if (rangeFilter == null) return (T) this;
        if (rangeFilter.getMinValue() != null) {
            String parameter = parameterPrefix + "Min";
            addParameter(parameter, rangeFilter.getMinValue());
            whereClauses.add(generateWhereClause(filter, minFilter.replace("rangeParam", parameter)));
        }
        if (rangeFilter.getMaxValue() != null) {
            String parameter = parameterPrefix + "Max";
            addParameter(parameter, rangeFilter.getMaxValue());
            whereClauses.add(generateWhereClause(filter, maxFilter.replace("rangeParam", parameter)));
        }
        return (T) this;
    }

    protected void addParameter(String name, Object value) {
        parameters.put(name, value);
    }

    protected void addWhereClauses(String clause) {
        whereClauses.add(clause);
    }

    protected void removeWhereClause(String clause) {
        whereClauses.remove(clause);
    }

    public T forCount() {
        this.forCount = true;
        return (T) this;
    }
}

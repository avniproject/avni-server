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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

public class BaseSubjectSearchQueryBuilder<T> {
    private final Logger logger;

    protected static final String ENCOUNTER_FILTER = "i.id in (select e.individual_id from encounter e where e.encounter_date_time is not null and e.is_voided is false\n";
    protected static final String PROGRAM_ENROLMENT_FILTER = "i.id in (select penr.individual_id from program_enrolment penr where penr.enrolment_date_time is not null and penr.is_voided is false\n";
    protected static final String PROGRAM_ENCOUNTER_FILTER = "i.id in (select penr.individual_id from program_enrolment penr join program_encounter penc on penc.program_enrolment_id = penr.id where penc.encounter_date_time is not null and penc.is_voided is false and penr.is_voided is false\n";
    protected static final String ADDRESS_FILTER = "i.address_id in (select al.id from address_level al where 1=1\n";
    protected static final String SEARCH_ALL_FILTER = "i.id in (select i.id from individual i join program_enrolment penr on penr.individual_id = i.id and penr.is_voided is false\n";

    private String orderByClause = "";

    protected final Set<String> whereClauses = new HashSet<>();
    private final Map<String, Object> parameters = new HashMap<>();
    private boolean forCount;
    private final Set<String> customFields = new HashSet<>();

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
                    put("FULLNAME", "concat_ws(' ',i.first_name,i.middle_name,i.last_name)");
                    put("SUBJECTTYPE", "st.name");
                    put("GENDER", "gender.name");
                    put("DATEOFBIRTH", "i.date_of_birth");
                    put("TITLE_LINEAGE", "tllv.title_lineage");
                }
            };

            this.orderByClause = "order by " + columnsMap.get(sortColumn.toUpperCase()) + " " + sortOrder.name() + ", i.id desc";
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
                addCustomFields(concept);
            });
        });
        return (T) this;
    }

    protected void addCustomFields(org.avni.server.domain.Concept concept) {
        if (concept.isCoded()) {
            customFields.add(String.format("multi_select_coded(i.observations -> '%s') as \"%s\"", concept.getUuid(), concept.getName()));
        } else if (concept.getDataType().equals(ConceptDataType.Date.toString())) {
            customFields.add(String.format("cast(i.observations ->> '%s' as date) as \"%s\"", concept.getUuid(), concept.getName()));
        } else {
            customFields.add(String.format("i.observations ->> '%s' as \"%s\"", concept.getUuid(), concept.getName()));
        }
    }

    private List<CustomSearchFields> getSearchFields(String subjectTypeUUID, List<CustomSearchFields> customSearchFields) {
        if (S.isEmpty(subjectTypeUUID)) return customSearchFields;
        SubjectTypeRepository subjectTypeRepository = ApplicationContextProvider.getContext().getBean(SubjectTypeRepository.class);
        SubjectType subjectType = subjectTypeRepository.findByUuid(subjectTypeUUID);
        return customSearchFields.stream().filter(csf -> csf.getSubjectTypeUUID().equals(subjectType.getUuid())).collect(Collectors.toList());
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

    public T withSearchAll(String searchString) {
        if (StringUtils.isEmpty(searchString)) return (T) this;
        String searchValue = "%" + searchString + "%";
        parameters.put("searchAll", searchValue);
        whereClauses.add(generateWhereClause(SEARCH_ALL_FILTER, "(cast(i.observations as text) ilike :searchAll or cast(penr.observations as text) ilike :searchAll)"));
        return (T) this;
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

            if (c.getDataType().equalsIgnoreCase("TEXT") || c.getDataType().equalsIgnoreCase("ID")) {
                String value = "%" + c.getValue() + "%";
                String param = "textValue" + ci;
                addParameter(param, value);
                whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()), tableAlias + ".observations->> :" + conceptUuidParam + " ilike :" + param));
            }

            if (c.getDataType().equalsIgnoreCase("NUMERIC")) {
                if (c.getWidget() != null && c.getWidget().equalsIgnoreCase("RANGE")) {
                    if (!StringUtils.isEmpty(c.getMinValue())) {
                        Float value = Float.parseFloat(c.getMinValue());
                        String param = "numericValueMin" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->> :" + conceptUuidParam + " as numeric)  >= :" + param));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        Float value = Float.parseFloat(c.getMaxValue());
                        String param = "numericValueMax" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as numeric)  <= :" + param));
                    }
                } else {
                    Float value = Float.parseFloat(c.getMinValue());
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
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as date)  >= cast(:" + param + " as date)"));
                    }
                    if (!StringUtils.isEmpty(c.getMaxValue())) {
                        String value = c.getMaxValue();
                        String param = "dateTimeValueMax" + ci;
                        addParameter(param, value);
                        whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as date)  <= cast(:" + param + " as date)"));
                    }
                } else {
                    String value = c.getMinValue();
                    String param = "dateTimeValueMin" + ci;
                    addParameter(param, value);
                    whereClauses.add(generateWhereClause(getFilterForSearchScope(c.getSearchScope()),"cast(" + tableAlias + ".observations ->>:" + conceptUuidParam + " as date)  = cast(:" + param + " as date)"));
                }
            }
        }

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

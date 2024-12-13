package org.avni.server.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.avni.messaging.domain.EntityType;
import org.avni.server.application.Form;
import org.avni.server.application.RuleType;
import org.avni.server.dao.*;
import org.avni.server.dao.application.FormRepository;
import org.avni.server.dao.individualRelationship.RuleFailureLogRepository;
import org.avni.server.domain.*;
import org.avni.server.framework.security.UserContextHolder;
import org.avni.server.util.ObjectMapperSingleton;
import org.avni.server.web.external.RuleServiceClient;
import org.avni.server.web.request.EntityTypeContract;
import org.avni.server.web.request.RuleRequest;
import org.avni.server.web.request.rules.RulesContractWrapper.*;
import org.avni.server.web.request.rules.constant.WorkFlowTypeEnum;
import org.avni.server.web.request.rules.constructWrappers.*;
import org.avni.server.web.request.rules.request.*;
import org.avni.server.web.request.rules.response.*;
import org.avni.server.web.request.rules.validateRules.RuleValidationService;
import org.avni.server.web.validation.ValidationException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class RuleService implements NonScopeAwareService {

    public static final String SOURCE_WEB = "Web";
    public static final String API_SUMMARY_RULE = "/api/summaryRule";
    public static final String API_MESSAGING_RULE = "/api/messagingRule";
    public static final String API_ENCOUNTER_ELIGIBILITY = "/api/encounterEligibility";
    public static final String RULES_MESSAGE_EXECUTE = "Rules : messageExecute";
    public static final String RULES_MESSAGE_SCHEDULE = "Rules : messageSchedule";

    private final Logger logger;
    private final RuleDependencyRepository ruleDependencyRepository;
    private final RuleRepository ruleRepository;
    private final Map<RuledEntityType, CHSRepository> ruledEntityRepositories;
    private final RuleServiceClient restClient;
    private final IndividualConstructionService individualConstructionService;
    private final RuleValidationService ruleValidationService;
    private final ProgramEncounterConstructionService programEncounterConstructionService;
    private final ProgramEnrolmentConstructionService programEnrolmentConstructionService;
    private final FormRepository formRepository;
    private final RuleFailureLogRepository ruleFailureLogRepository;
    private final ObservationService observationService;
    private final IndividualContractBuilderServices contractBuilderServices;
    private final EntityRetrieverService entityRetrieverService;
    private final IndividualService individualService;
    private final RuleServiceEntityContractBuilder ruleServiceEntityContractBuilder;
    private final UserRepository userRepository;
    private final OrganisationRepository organisationRepository;

    @Autowired
    public RuleService(RuleDependencyRepository ruleDependencyRepository,
                       RuleRepository ruleRepository,
                       FormRepository formRepository,
                       ProgramRepository programRepository,
                       EncounterTypeRepository encounterTypeRepository,
                       RuleServiceClient restClient,
                       IndividualConstructionService individualConstructionService,
                       RuleValidationService ruleValidationService,
                       ProgramEncounterConstructionService programEncounterConstructionService,
                       ProgramEnrolmentConstructionService programEnrolmentConstructionService,
                       RuleFailureLogRepository ruleFailureLogRepository,
                       ObservationService observationService,
                       IndividualContractBuilderServices contractBuilderServices,
                       EntityRetrieverService entityRetrieverService, IndividualService individualService, RuleServiceEntityContractBuilder ruleServiceEntityContractBuilder, UserRepository userRepository, OrganisationRepository organisationRepository) {
        this.ruleFailureLogRepository = ruleFailureLogRepository;
        this.observationService = observationService;
        this.contractBuilderServices = contractBuilderServices;
        this.entityRetrieverService = entityRetrieverService;
        this.individualService = individualService;
        this.ruleServiceEntityContractBuilder = ruleServiceEntityContractBuilder;
        this.userRepository = userRepository;
        this.organisationRepository = organisationRepository;
        logger = LoggerFactory.getLogger(this.getClass());
        this.ruleDependencyRepository = ruleDependencyRepository;
        this.ruleRepository = ruleRepository;
        this.ruledEntityRepositories = new HashMap<RuledEntityType, CHSRepository>() {{
            put(RuledEntityType.Form, formRepository);
            put(RuledEntityType.Program, programRepository);
            put(RuledEntityType.EncounterType, encounterTypeRepository);
        }};
        this.restClient = restClient;
        this.individualConstructionService = individualConstructionService;
        this.ruleValidationService = ruleValidationService;
        this.programEncounterConstructionService = programEncounterConstructionService;
        this.programEnrolmentConstructionService = programEnrolmentConstructionService;
        this.formRepository = formRepository;
    }

    @Transactional
    public RuleDependency createDependency(String ruleCode, String ruleHash) {
        RuleDependency ruleDependency = ruleDependencyRepository
                .findByOrganisationId(UserContextHolder.getUserContext().getOrganisation().getId());
        if (ruleDependency == null) ruleDependency = new RuleDependency();
        if (ruleHash.equals(ruleDependency.getChecksum())) return ruleDependency;
        ruleDependency.setCode(ruleCode);
        ruleDependency.setChecksum(ruleHash);
        ruleDependency.assignUUIDIfRequired();
        logger.info(String.format("Rule dependency with UUID: %s", ruleDependency.getUuid()));
        return ruleDependencyRepository.save(ruleDependency);
    }

    private Rule _setCommonAttributes(Rule rule, RuleRequest ruleRequest) {
        rule.setUuid(ruleRequest.getUuid());
        rule.setData(new RuleData(ruleRequest.getData()));
        rule.setName(ruleRequest.getName());
        rule.setFnName(ruleRequest.getFnName());
        rule.setType(RuleType.valueOf(StringUtils.capitalize(ruleRequest.getType())));
        rule.setExecutionOrder(ruleRequest.getExecutionOrder());
        rule.setVoided(false);
        return rule;
    }

    @Transactional
    public Rule createOrUpdate(RuleRequest ruleRequest, Organisation organisation) {
        RuleDependency ruleDependency = ruleDependencyRepository.findByOrganisationId(organisation.getId());
        String ruleUUID = ruleRequest.getUuid();
        Rule rule = ruleRepository.findByUuid(ruleUUID);
        if (rule == null) rule = new Rule();
        rule.setRuleDependency(ruleDependency);
        rule = this._setCommonAttributes(rule, ruleRequest);

        checkEntityExists(ruleRequest);

        rule.setEntity(ruleRequest.getEntity());

        logger.info(String.format("Creating Rule with UUID '%s', Name '%s', Type '%s', Entity '%s'",
                rule.getUuid(), rule.getName(), rule.getType(), ruleRequest.getEntityType()));

        return ruleRepository.save(rule);
    }

    @Transactional
    public void createOrUpdate(List<RuleRequest> rules) {
        Organisation organisation = UserContextHolder.getUserContext().getOrganisation();
        List<Rule> rulesFromDB = ruleRepository.findByOrganisationId(organisation.getId());
        List<String> newRuleUUIDs = rules.stream()
                .map(rule -> createOrUpdate(rule, organisation))
                .map(Rule::getUuid)
                .collect(Collectors.toList());

        Stream<Rule> deletedRules = rulesFromDB.stream().filter(r -> !newRuleUUIDs.contains(r.getUuid()));

        deletedRules.peek(vr -> vr.setVoided(true)).forEach(ruleRepository::save);
    }

    public RuleResponseEntity executeProgramSummaryRule(ProgramEnrolment programEnrolment) throws RuleExecutionException {
        RuleResponseEntity ruleResponseEntity = new RuleResponseEntity();
        if (programEnrolment == null) {
            ruleResponseEntity.setStatus(HttpStatus.NOT_FOUND.toString());
            return ruleResponseEntity;
        }
        RuleRequestEntity rule = new RuleRequestEntity();
        Program program = programEnrolment.getProgram();
        rule.setProgramSummaryCode(program.getEnrolmentSummaryRule());
        String workFlowType = WorkFlowTypeEnum.PROGRAM_SUMMARY.getWorkFlowTypeName();
        rule.setWorkFlowType(workFlowType);
        rule.setFormUuid(program.getUuid());
        rule.setRuleType("Program Summary");
        ProgramEnrolmentContract programEnrolmentContract = ruleServiceEntityContractBuilder.toContract(programEnrolment);
        programEnrolmentContract.setRule(rule);
        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, SOURCE_WEB, "Rules : " + workFlowType, programEnrolment.getUuid());
        ruleResponseEntity = createHttpHeaderAndSendRequest(API_SUMMARY_RULE, programEnrolmentContract, ruleFailureLog, RuleResponseEntity.class);
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    public RuleResponseEntity executeSubjectSummaryRule(Individual individual) throws RuleExecutionException {
        if (individual == null) {
            RuleResponseEntity ruleResponseEntity = new RuleResponseEntity();
            ruleResponseEntity.setStatus(HttpStatus.NOT_FOUND.toString());
            return ruleResponseEntity;
        }
        RuleRequestEntity rule = new RuleRequestEntity();
        SubjectType subjectType = individual.getSubjectType();
        rule.setSubjectSummaryCode(subjectType.getSubjectSummaryRule());
        String workFlowType = WorkFlowTypeEnum.SUBJECT_SUMMARY.getWorkFlowTypeName();
        rule.setWorkFlowType(workFlowType);
        rule.setFormUuid(subjectType.getUuid());
        rule.setRuleType("Subject Summary");
        IndividualContract individualContract = ruleServiceEntityContractBuilder.toContract(individual);
        individualContract.setRule(rule);
        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, SOURCE_WEB, "Rules : " + workFlowType, individual.getUuid());
        RuleResponseEntity ruleResponseEntity = createHttpHeaderAndSendRequest(API_SUMMARY_RULE, individualContract, ruleFailureLog, RuleResponseEntity.class);
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    public EligibilityRuleResponseEntity executeEligibilityRule(Individual individual, List<EncounterType> encounterTypes) throws RuleExecutionException {
        IndividualContract individualContract = ruleServiceEntityContractBuilder.toContract(individual);
        individualContract.setEnrolments(individual
                .getProgramEnrolments()
                .stream()
                .map(ruleServiceEntityContractBuilder::toContract)
                .collect(Collectors.toList())
        );
        List<EntityTypeContract> entityTypeContracts = encounterTypes.stream().map(EntityTypeContract::fromEncounterType).collect(Collectors.toList());
        EntityEligibilityRuleRequest ruleRequest = new EntityEligibilityRuleRequest(individualContract, entityTypeContracts, RuleEntityType.EncounterType.name());
        return createHttpHeaderAndSendRequest(API_ENCOUNTER_ELIGIBILITY, ruleRequest, null, EligibilityRuleResponseEntity.class);
    }

    public EligibilityRuleResponseEntity executeProgramEligibilityCheckRule(Individual individual, List<Program> programs) throws RuleExecutionException {
        EligibilityRuleResponseEntity ruleResponseEntity = new EligibilityRuleResponseEntity();
        IndividualContract individualContract = ruleServiceEntityContractBuilder.toContract(individual);
        List<EntityTypeContract> entityTypeContracts = programs.stream().map(EntityTypeContract::fromProgram).collect(Collectors.toList());
        EntityEligibilityRuleRequest ruleRequest = new EntityEligibilityRuleRequest(individualContract, entityTypeContracts, RuleEntityType.Program.name());
        return createHttpHeaderAndSendRequest(API_ENCOUNTER_ELIGIBILITY, ruleRequest, null, EligibilityRuleResponseEntity.class);
    }

    public ScheduleRuleResponseEntity executeScheduleRule(String entityType, Long entityId, String scheduleRule) throws RuleExecutionException {
        String rulesMessage = RULES_MESSAGE_SCHEDULE;
        Class<ScheduleRuleResponseEntity> responseType = ScheduleRuleResponseEntity.class;
        CHSEntity entity = entityRetrieverService.getEntity(entityType, entityId);
        RuleServerEntityContract contract = ruleServiceEntityContractBuilder.toContract(entityType, entity);
        return (ScheduleRuleResponseEntity) getBaseRuleResponseEntity(entityType, scheduleRule, rulesMessage, responseType, contract, entity.getUuid());
    }

    public MessageRuleResponseEntity executeMessageRule(String entityType, Long entityId, String messageRule) throws RuleExecutionException {
        String rulesMessage = RULES_MESSAGE_EXECUTE;
        Class<MessageRuleResponseEntity> responseType = MessageRuleResponseEntity.class;
        CHSEntity entity = entityRetrieverService.getEntity(entityType, entityId);
        RuleServerEntityContract contract = ruleServiceEntityContractBuilder.toContract(entityType, entity);
        return (MessageRuleResponseEntity) getBaseRuleResponseEntity(entityType, messageRule, rulesMessage, responseType, contract, entity.getUuid());
    }

    public ScheduleRuleResponseEntity executeScheduleRuleForEntityTypeUser(Long entityId, String scheduleRule) throws RuleExecutionException {
        String entityType = EntityType.User.name();
        String rulesMessage = RULES_MESSAGE_SCHEDULE;
        Class<ScheduleRuleResponseEntity> responseType = ScheduleRuleResponseEntity.class;
        User entity = userRepository.getOne(entityId);
        Organisation organisation = organisationRepository.findOne(entity.getOrganisationId());
        RuleServerEntityContract contract = UserContract.fromUser(entity, organisation);
        return (ScheduleRuleResponseEntity) getBaseRuleResponseEntity(entityType, scheduleRule, rulesMessage, responseType, contract, entity.getUuid());
    }

    public MessageRuleResponseEntity executeMessageRuleForEntityTypeUser(Long entityId, String messageRule) throws RuleExecutionException {
        String entityType = EntityType.User.name();
        String rulesMessage = RULES_MESSAGE_EXECUTE;
        Class<MessageRuleResponseEntity> responseType = MessageRuleResponseEntity.class;
        User entity = userRepository.getOne(entityId);
        Organisation organisation = organisationRepository.findOne(entity.getOrganisationId());
        RuleServerEntityContract contract = UserContract.fromUser(entity, organisation);
        return (MessageRuleResponseEntity) getBaseRuleResponseEntity(entityType, messageRule, rulesMessage, responseType, contract, entity.getUuid());
    }

    private BaseRuleResponseEntity getBaseRuleResponseEntity(String entityType, String rule, String rulesMessage, Class<? extends BaseRuleResponseEntity> responseType, RuleServerEntityContract contract,
                                                             String entityUuid) throws RuleExecutionException {
        MessageRequestEntity ruleRequest = new MessageRequestEntity(contract, rule, entityType);
        BaseRuleRequest baseRuleRequest = new BaseRuleRequest();
        baseRuleRequest.setRuleType(entityType);
        baseRuleRequest.setFormUuid(entityUuid);
        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(baseRuleRequest, SOURCE_WEB, rulesMessage, String.valueOf(entityUuid));
        return createHttpHeaderAndSendRequest(API_MESSAGING_RULE, ruleRequest, ruleFailureLog, responseType);
    }

    public RuleResponseEntity executeServerSideRules(RequestEntityWrapper requestEntityWrapper) throws RuleExecutionException {
        RuleRequestEntity rule = requestEntityWrapper.getRule();
        Form form = formRepository.findByUuid(rule.getFormUuid());
        rule.setDecisionCode(form.getDecisionRule());
        rule.setVisitScheduleCode(form.getVisitScheduleRule());
        rule.setChecklistCode(form.getChecklistsRule());

        Object entity = null;
        String entityUuid = null;
        String workFlowType = requestEntityWrapper.getRule().getWorkFlowType();
        if(StringUtils.isEmpty(rule.getRuleType())) {
            rule.setRuleType(workFlowType);
        }
        switch (WorkFlowTypeEnum.findByValue(workFlowType.toLowerCase())) {
            case PROGRAM_ENROLMENT:
                ProgramEnrolmentRequestEntity programEnrolmentRequestEntity = requestEntityWrapper.getProgramEnrolmentRequestEntity();
                entityUuid = programEnrolmentRequestEntity.getUuid();
                ProgramEnrolmentContract programEnrolmentContract = programEnrolmentConstructionService.constructProgramEnrolmentContract(programEnrolmentRequestEntity);
                programEnrolmentContract.setRule(rule);
                programEnrolmentContract.setVisitSchedules(new ArrayList<>());
                programEnrolmentContract.setChecklistDetails(programEnrolmentConstructionService.constructChecklistDetailRequest());
                entity = programEnrolmentContract;
                break;
            case PROGRAM_ENCOUNTER:
                ProgramEncounterRequestEntity programEncounterRequestEntity = requestEntityWrapper.getProgramEncounterRequestEntity();
                entityUuid = programEncounterRequestEntity.getUuid();
                ProgramEncounterContract programEncounterContract = programEncounterConstructionService.constructProgramEncounterContract(programEncounterRequestEntity);
                programEncounterContract.setRule(rule);
                programEncounterContract.setVisitSchedules(programEncounterConstructionService.constructProgramEnrolmentVisitScheduleContract(programEncounterRequestEntity));
                entity = programEncounterContract;
                break;
            case ENCOUNTER:
                EncounterRequestEntity encounterRequestEntity = requestEntityWrapper.getEncounterRequestEntity();
                entityUuid = encounterRequestEntity.getUuid();
                EncounterContract encounterContract = programEncounterConstructionService.constructEncounterContract(encounterRequestEntity);
                encounterContract.setRule(rule);
                encounterContract.setVisitSchedules(programEncounterConstructionService.constructIndividualVisitScheduleContract(encounterRequestEntity));
                entity = encounterContract;
                break;
            case INDIVIDUAL:
                IndividualRequestEntity individualRequestEntity = requestEntityWrapper.getIndividualRequestEntity();
                entityUuid = individualRequestEntity.getUuid();
                IndividualContract individualContract = new IndividualContractBuilder(contractBuilderServices, individualService).build(individualRequestEntity);
                individualContract.setRule(rule);
                individualContract.setVisitSchedules(new ArrayList<>());
                entity = individualContract;
                break;
        }

        RuleFailureLog ruleFailureLog = ruleValidationService.generateRuleFailureLog(rule, SOURCE_WEB, "Rules : " + workFlowType, entityUuid);
        RuleResponseEntity ruleResponseEntity = createHttpHeaderAndSendRequest("/api/rules", entity, ruleFailureLog, RuleResponseEntity.class);
        setObservationsOnResponse(workFlowType, ruleResponseEntity);
        return ruleResponseEntity;
    }

    @Override
    public boolean isNonScopeEntityChanged(DateTime lastModifiedDateTime) {
        return ruleRepository.existsByLastModifiedDateTimeGreaterThan(lastModifiedDateTime);
    }

    private void setObservationsOnResponse(String workFlowType, RuleResponseEntity ruleResponseEntity) {
        DecisionResponseEntity decisions = ruleResponseEntity.getDecisions();
        List<KeyValueResponse> summaries = ruleResponseEntity.getSummaries();
        WorkFlowTypeEnum workFlowTypeEnum = WorkFlowTypeEnum.findByValue(workFlowType.toLowerCase());

        switch (workFlowTypeEnum) {
            case PROGRAM_ENROLMENT:
                decisions.setEnrolmentObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEnrolmentDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case PROGRAM_ENCOUNTER:
                decisions.setEncounterObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEncounterDecisions(), workFlowTypeEnum));
                decisions.setEnrolmentObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEnrolmentDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case ENCOUNTER:
                decisions.setEncounterObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getEncounterDecisions(), workFlowTypeEnum));
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case INDIVIDUAL:
                decisions.setRegistrationObservations(observationService.createObservationContractsFromKeyValueResponse(decisions.getRegistrationDecisions(), workFlowTypeEnum));
                break;
            case PROGRAM_SUMMARY:
            case SUBJECT_SUMMARY:
                ruleResponseEntity.setSummaryObservations(observationService.createObservationContractsFromKeyValueResponse(summaries, workFlowTypeEnum));
                break;
        }
    }

    private <R extends BaseRuleResponseEntity> R createHttpHeaderAndSendRequest(String url, Object contractObject, RuleFailureLog ruleFailureLog, Class<R> responseType) throws RuleExecutionException {
        try {
            ObjectMapper mapper = ObjectMapperSingleton.getObjectMapper();
            R ruleResponseEntity = (R) restClient.post(url, contractObject, responseType);
            if (ruleResponseEntity.getStatus().equals("failure")) {
                RuleError ruleError = ruleResponseEntity.getError();
                saveRuleError(ruleFailureLog, ruleError.getMessage(), ruleError.getStack());
            }
            return ruleResponseEntity;
        } catch (Exception e) {
            logger.error("Error while executing rule", e);
            saveRuleError(ruleFailureLog, e.getMessage(), getStackTrace(e));
            RuleError ruleError = new RuleError();
            ruleError.setMessage(e.getMessage());
            ruleError.setStack(getStackTrace(e));
            throw new RuleExecutionException(ruleError);
        }
    }

    private String getStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }

    private void saveRuleError(RuleFailureLog ruleFailureLog, String message, String stack) {
        if (ruleFailureLog != null) {
            ruleFailureLog.setErrorMessage(message);
            ruleFailureLog.setStacktrace(stack);
            ruleFailureLogRepository.save(ruleFailureLog);
        }
    }

    private void checkEntityExists(RuleRequest ruleRequest) {
        String entityUUID = ruleRequest.getEntityUUID();
        if (!RuledEntityType.isNone(ruleRequest.getEntityType())) {
            CHSRepository chsRepository = ruledEntityRepositories.get(ruleRequest.getEntityType());
            if (chsRepository.findByUuid(entityUUID) == null) {
                throw new ValidationException(String.format("%s with uuid: %s not found for rule with uuid: %s",
                        ruleRequest.getEntityType(), entityUUID, ruleRequest.getUuid()));
            }
        }
    }
}
